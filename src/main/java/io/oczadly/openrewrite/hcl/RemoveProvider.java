package io.oczadly.openrewrite.hcl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Hcl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveProvider extends ScanningRecipe<RemoveProvider.ScanState> {

    @Value
    public static class ScanState {
        Set<String> matchingDirectories = ConcurrentHashMap.newKeySet();

        void markDirectoryMatched(Hcl.ConfigFile configFile) {
            String path = configFile.getSourcePath().toString();
            String directory = ProviderRecipeSupport.directoryOf(path);
            matchingDirectories.add(directory);
        }

        boolean isDirectoryMatched(Hcl.ConfigFile configFile, @Nullable String filePattern) {
            String directory = ProviderRecipeSupport.directoryOf(configFile.getSourcePath().toString());
            String currentPath = configFile.getSourcePath().toString();
            return matchingDirectories.contains(directory)
                   && (filePattern == null || ProviderRecipeSupport.matchesPattern(currentPath, filePattern));
        }
    }

    @Option(displayName = "Provider name", description = "The provider name to remove")
    @Nullable
    String providerName;

    @Option(displayName = "Remove configuration", description = "Whether to remove matching provider \"name\" block(s). Defaults to true.", required = false)
    @Nullable
    Boolean removeConfiguration;

    @Option(displayName = "Source", description = "Optional module source filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String source;

    @Option(displayName = "Version", description = "Optional module semantic version constraint filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String version;

    @Option(displayName = "Module name", description = "Optional module block name filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String moduleName;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false)
    @Nullable
    String filePattern;

    @JsonCreator
    public RemoveProvider(@JsonProperty("providerName") @Nullable String providerName,
                          @JsonProperty("removeConfiguration") @Nullable Boolean removeConfiguration,
                          @JsonProperty("source") @Nullable String source,
                          @JsonProperty("version") @Nullable String version,
                          @JsonProperty("moduleName") @Nullable String moduleName,
                          @JsonProperty("filePattern") @Nullable String filePattern) {
        this.providerName = providerName;
        this.removeConfiguration = removeConfiguration;
        this.source = source;
        this.version = version;
        this.moduleName = moduleName;
        this.filePattern = filePattern;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Remove provider";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Removes a provider entry from terraform.required_providers and optionally removes matching provider configuration blocks.";
    }

    @Override
    public @NonNull ScanState getInitialValue(@Nullable ExecutionContext ctx) {
        return new ScanState();
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getScanner(@Nullable ScanState acc) {
        String resolvedModuleSource = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(source, "source");
        String resolvedModuleVersion = TopLevelBlockRecipeSupport.resolveOptionalVersionFilterValue(version);
        String resolvedModuleName = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(moduleName, "moduleName");

        return ProviderRecipeSupport.scopedVisitor("**/*.tf", new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);
                if (acc != null && ProviderRecipeSupport.matchesModuleFilters(visited, resolvedModuleName, resolvedModuleSource, resolvedModuleVersion)) {
                    acc.markDirectoryMatched(visited);
                }
                return visited;
            }
        });
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor(@Nullable ScanState acc) {
        String resolvedProviderName = TopLevelBlockRecipeSupport.resolveRequiredValue(providerName, "providerName");
        boolean shouldRemoveConfiguration = removeConfiguration == null || removeConfiguration;

        return ProviderRecipeSupport.scopedVisitor(filePattern, new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);

                if (acc == null || !acc.isDirectoryMatched(visited, filePattern)) {
                    return visited;
                }

                boolean changed = false;
                Hcl.ConfigFile modified = visited;

                Hcl.Block terraformBlock = ProviderRecipeSupport.findTopLevelBlock(modified, "terraform");
                if (terraformBlock != null) {
                    Hcl.Block requiredProvidersBlock = ProviderRecipeSupport.findNestedBlock(terraformBlock, "required_providers");
                    if (requiredProvidersBlock != null) {
                        Hcl.Block updatedRequiredProviders = removeProviderEntry(requiredProvidersBlock, resolvedProviderName);
                        if (updatedRequiredProviders != requiredProvidersBlock) {
                            Hcl.Block updatedTerraform = ProviderRecipeSupport.replaceNestedBlock(terraformBlock, requiredProvidersBlock, updatedRequiredProviders);
                            modified = ProviderRecipeSupport.replaceTopLevelBlock(modified, terraformBlock, updatedTerraform);
                            changed = true;
                        }
                    }
                }

                if (shouldRemoveConfiguration) {
                    Hcl.ConfigFile afterConfigurationRemoval = removeProviderBlocks(modified, resolvedProviderName);
                    if (afterConfigurationRemoval != modified) {
                        modified = afterConfigurationRemoval;
                        changed = true;
                    }
                }

                if (!changed) {
                    return visited;
                }

                doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                return modified;
            }

            private Hcl.Block removeProviderEntry(Hcl.Block requiredProvidersBlock, String providerName) {
                List<BodyContent> originalBody = requiredProvidersBlock.getBody();
                List<BodyContent> updatedBody = new ArrayList<>(originalBody.size());
                boolean removed = false;

                for (BodyContent bodyContent : originalBody) {
                    if (bodyContent instanceof Hcl.Attribute) {
                        Hcl.Attribute attribute = (Hcl.Attribute) bodyContent;
                        if (providerName.equals(attribute.getSimpleName())) {
                            removed = true;
                            continue;
                        }
                    }
                    updatedBody.add(bodyContent);
                }

                return removed ? requiredProvidersBlock.withBody(updatedBody) : requiredProvidersBlock;
            }

            private Hcl.ConfigFile removeProviderBlocks(Hcl.ConfigFile configFile, String providerName) {
                List<BodyContent> updatedBody = new ArrayList<>(configFile.getBody().size());
                boolean removed = false;

                for (BodyContent bodyContent : configFile.getBody()) {
                    if (!(bodyContent instanceof Hcl.Block)) {
                        updatedBody.add(bodyContent);
                        continue;
                    }

                    Hcl.Block block = (Hcl.Block) bodyContent;
                    if (!ProviderRecipeSupport.isBlockType(block, "provider") || !ProviderRecipeSupport.matchesProviderNameLabel(block, providerName)) {
                        updatedBody.add(bodyContent);
                        continue;
                    }

                    removed = true;
                }

                return removed ? configFile.withBody(updatedBody) : configFile;
            }
        });
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "source", source);
        validated = TopLevelBlockRecipeSupport.validateOptionalVersionConstraint(validated, version);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "moduleName", moduleName);
        return validated;
    }
}
