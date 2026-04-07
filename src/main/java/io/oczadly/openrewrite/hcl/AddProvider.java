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
import org.openrewrite.hcl.HclParser;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddProvider extends ScanningRecipe<AddProvider.ScanState> {

    @Value
    public static class ScanState {
        ConcurrentMap<String, DirectoryState> directories = new ConcurrentHashMap<>();

        void addFile(Hcl.ConfigFile configFile,
                     boolean moduleFiltersMatched,
                     boolean hasTerraformBlock,
                     boolean hasRequiredProvidersBlock) {
            String path = configFile.getSourcePath().toString();
            String directory = ProviderRecipeSupport.directoryOf(path);
            DirectoryState state = directories.computeIfAbsent(directory, ignored -> new DirectoryState());
            if (moduleFiltersMatched) {
                state.matchingFiles.add(path);
            }
            if (hasTerraformBlock) {
                state.terraformFiles.add(path);
            }
            if (hasRequiredProvidersBlock) {
                state.requiredProvidersFiles.add(path);
            }
        }

        @Nullable
        String targetFileFor(Hcl.ConfigFile configFile, @Nullable String filePattern) {
            String directory = ProviderRecipeSupport.directoryOf(configFile.getSourcePath().toString());
            DirectoryState state = directories.get(directory);
            if (state == null || state.matchingFiles.isEmpty()) {
                return null;
            }

            String candidate = firstMatching(state.requiredProvidersFiles, filePattern);
            if (candidate != null) {
                return candidate;
            }
            candidate = firstMatching(state.terraformFiles, filePattern);
            if (candidate != null) {
                return candidate;
            }
            return firstMatching(state.matchingFiles, filePattern);
        }

        @Nullable
        private String firstMatching(NavigableSet<String> candidates, @Nullable String filePattern) {
            for (String candidate : candidates) {
                if (filePattern == null || ProviderRecipeSupport.matchesPattern(candidate, filePattern)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    @Value
    public static class DirectoryState {
        NavigableSet<String> matchingFiles = new ConcurrentSkipListSet<>();
        NavigableSet<String> terraformFiles = new ConcurrentSkipListSet<>();
        NavigableSet<String> requiredProvidersFiles = new ConcurrentSkipListSet<>();
    }

    @Option(displayName = "Provider name", description = "The provider name to add (for example 'azapi' or 'azuread')")
    @Nullable
    String providerName;

    @Option(displayName = "Provider source", description = "Optional provider source (for example 'azure/azapi'). If omitted, a simple version string entry is created.", required = false)
    @Nullable
    String providerSource;

    @Option(displayName = "Provider version", description = "Version constraint to add in required_providers")
    @Nullable
    String providerVersion;

    @Option(displayName = "Provider configuration", description = "Optional provider block body to append as provider \"name\" { ... }", required = false)
    @Nullable
    String configuration;

    @Option(displayName = "Source", description = "Optional module source filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String source;

    @Option(displayName = "Version", description = "Optional module version filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String version;

    @Option(displayName = "Module name", description = "Optional module block name filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String moduleName;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false)
    @Nullable
    String filePattern;

    @JsonCreator
    public AddProvider(@JsonProperty("providerName") @Nullable String providerName,
                       @JsonProperty("providerSource") @Nullable String providerSource,
                       @JsonProperty("providerVersion") @Nullable String providerVersion,
                       @JsonProperty("configuration") @Nullable String configuration,
                       @JsonProperty("source") @Nullable String source,
                       @JsonProperty("version") @Nullable String version,
                       @JsonProperty("moduleName") @Nullable String moduleName,
                       @JsonProperty("filePattern") @Nullable String filePattern) {
        this.providerName = providerName;
        this.providerSource = providerSource;
        this.providerVersion = providerVersion;
        this.configuration = configuration;
        this.source = source;
        this.version = version;
        this.moduleName = moduleName;
        this.filePattern = filePattern;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Add provider";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Adds a provider entry to terraform.required_providers and optionally appends a provider configuration block.";
    }

    @Override
    public @NonNull ScanState getInitialValue(@Nullable ExecutionContext ctx) {
        return new ScanState();
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getScanner(@Nullable ScanState acc) {
        String resolvedModuleSource = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(source, "source");
        String resolvedModuleVersion = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(version, "version");
        String resolvedModuleName = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(moduleName, "moduleName");

        return ProviderRecipeSupport.scopedVisitor("**/*.tf", new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);
                Hcl.Block terraformBlock = ProviderRecipeSupport.findTopLevelBlock(visited, "terraform");
                Hcl.Block requiredProvidersBlock = terraformBlock == null ? null : ProviderRecipeSupport.findNestedBlock(terraformBlock, "required_providers");
                boolean moduleFiltersMatched = ProviderRecipeSupport.matchesModuleFilters(visited, resolvedModuleName, resolvedModuleSource, resolvedModuleVersion);

                if (acc != null) {
                    acc.addFile(
                        visited,
                        moduleFiltersMatched,
                        terraformBlock != null,
                        requiredProvidersBlock != null
                    );
                }
                return visited;
            }
        });
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor(@Nullable ScanState acc) {
        String resolvedProviderName = TopLevelBlockRecipeSupport.resolveRequiredValue(providerName, "providerName");
        String resolvedProviderVersion = TopLevelBlockRecipeSupport.resolveRequiredValue(providerVersion, "providerVersion");
        String resolvedProviderSource = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(providerSource, "providerSource");
        String resolvedConfiguration = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(configuration, "configuration");

        HclParser parser = HclParser.builder().build();

        return ProviderRecipeSupport.scopedVisitor(filePattern, new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);

                String targetFile = null;
                if (acc != null) {
                    targetFile = acc.targetFileFor(visited, filePattern);
                }
                if (targetFile == null || !targetFile.equals(visited.getSourcePath().toString())) {
                    return visited;
                }

                boolean changed = false;
                Hcl.ConfigFile modified = visited;

                if (!hasProviderInRequiredProviders(modified, resolvedProviderName)) {
                    modified = addProviderToRequiredProviders(modified, parser, resolvedProviderName, resolvedProviderSource, resolvedProviderVersion);
                    changed = true;
                }

                if (resolvedConfiguration != null && !hasProviderConfigurationBlock(modified, resolvedProviderName)) {
                    modified = addProviderConfigurationBlock(modified, parser, resolvedProviderName, resolvedConfiguration);
                    changed = true;
                }

                if (!changed) {
                    return visited;
                }

                doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                return modified;
            }

            private boolean hasProviderInRequiredProviders(Hcl.ConfigFile configFile, String providerName) {
                Hcl.Block terraformBlock = ProviderRecipeSupport.findTopLevelBlock(configFile, "terraform");
                if (terraformBlock == null) {
                    return false;
                }

                Hcl.Block requiredProvidersBlock = ProviderRecipeSupport.findNestedBlock(terraformBlock, "required_providers");
                if (requiredProvidersBlock == null) {
                    return false;
                }

                for (BodyContent bodyContent : requiredProvidersBlock.getBody()) {
                    if (bodyContent instanceof Hcl.Attribute) {
                        Hcl.Attribute attribute = (Hcl.Attribute) bodyContent;
                        if (providerName.equals(attribute.getSimpleName())) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private Hcl.ConfigFile addProviderToRequiredProviders(Hcl.ConfigFile configFile,
                                                                  HclParser parser,
                                                                  String providerName,
                                                                  @Nullable String providerSource,
                                                                  String providerVersion) {
                Hcl.Block terraformBlock = ProviderRecipeSupport.findTopLevelBlock(configFile, "terraform");
                if (terraformBlock == null) {
                    return addTerraformBlock(configFile, parser, providerName, providerSource, providerVersion);
                }

                Hcl.Block requiredProvidersBlock = ProviderRecipeSupport.findNestedBlock(terraformBlock, "required_providers");
                Hcl.Block updatedTerraform;
                if (requiredProvidersBlock == null) {
                    String requiredProvidersIndent = ProviderRecipeSupport.childIndent(terraformBlock, "  ");
                    Hcl.Block requiredProvidersToAdd = parseRequiredProvidersBlockForTerraform(parser, providerName, providerSource, providerVersion)
                        .withPrefix(Space.format("\n" + requiredProvidersIndent));

                    List<BodyContent> terraformBody = new ArrayList<>(terraformBlock.getBody());
                    terraformBody.add(requiredProvidersToAdd);
                    updatedTerraform = terraformBlock.withBody(terraformBody);
                } else {
                    String requiredProvidersPrefix = requiredProvidersBlock.getPrefix().getWhitespace();
                    int newlineIndex = requiredProvidersPrefix.lastIndexOf('\n');
                    String requiredProvidersIndent = newlineIndex >= 0 ? requiredProvidersPrefix.substring(newlineIndex + 1) : "";
                    String entryIndent = ProviderRecipeSupport.childIndent(requiredProvidersBlock, requiredProvidersIndent + "  ");

                    List<String> renderedEntries = new ArrayList<>(requiredProvidersBlock.getBody().size() + 1);
                    for (BodyContent bodyContent : requiredProvidersBlock.getBody()) {
                        renderedEntries.add(ProviderRecipeSupport.indentBlockBody(bodyContent.printTrimmed(getCursor()), entryIndent));
                    }
                    renderedEntries.add(renderProviderEntry(providerName, providerSource, providerVersion, entryIndent));

                    Hcl.Block updatedRequiredProviders = parseRequiredProvidersBlock(parser, renderedEntries, requiredProvidersIndent)
                        .withPrefix(requiredProvidersBlock.getPrefix());
                    updatedTerraform = ProviderRecipeSupport.replaceNestedBlock(terraformBlock, requiredProvidersBlock, updatedRequiredProviders);
                }

                return ProviderRecipeSupport.replaceTopLevelBlock(configFile, terraformBlock, updatedTerraform);
            }

            private Hcl.ConfigFile addTerraformBlock(Hcl.ConfigFile configFile,
                                                     HclParser parser,
                                                     String providerName,
                                                     @Nullable String providerSource,
                                                     String providerVersion) {
                Hcl.Block terraformToAdd = parseTerraformBlock(parser, providerName, providerSource, providerVersion);
                if (!configFile.getBody().isEmpty()) {
                    terraformToAdd = terraformToAdd.withPrefix(Space.format("\n\n"));
                }

                List<BodyContent> updatedBody = new ArrayList<>(configFile.getBody());
                updatedBody.add(terraformToAdd);
                return configFile.withBody(updatedBody);
            }

            private boolean hasProviderConfigurationBlock(Hcl.ConfigFile configFile, String providerName) {
                for (BodyContent bodyContent : configFile.getBody()) {
                    if (!(bodyContent instanceof Hcl.Block)) {
                        continue;
                    }

                    Hcl.Block block = (Hcl.Block) bodyContent;
                    if (!ProviderRecipeSupport.isBlockType(block, "provider")) {
                        continue;
                    }

                    if (ProviderRecipeSupport.matchesProviderNameLabel(block, providerName)) {
                        return true;
                    }
                }
                return false;
            }

            private Hcl.ConfigFile addProviderConfigurationBlock(Hcl.ConfigFile configFile,
                                                                 HclParser parser,
                                                                 String providerName,
                                                                 String providerConfiguration) {
                Hcl.Block providerBlock = parseProviderBlock(parser, providerName, providerConfiguration);
                if (!configFile.getBody().isEmpty()) {
                    providerBlock = providerBlock.withPrefix(Space.format("\n\n"));
                }

                List<BodyContent> updatedBody = new ArrayList<>(configFile.getBody());
                updatedBody.add(providerBlock);
                return configFile.withBody(updatedBody);
            }

            private Hcl.Block parseTerraformBlock(HclParser parser,
                                                  String providerName,
                                                  @Nullable String providerSource,
                                                  String providerVersion) {
                String providerEntry = renderProviderEntry(providerName, providerSource, providerVersion, "    ");
                String terraformText = "terraform {\n"
                                       + "  required_providers {\n"
                                       + providerEntry + "\n"
                                       + "  }\n"
                                       + "}\n";
                return ProviderRecipeSupport.parseSingleBlock(parser, terraformText, "terraform block");
            }

            private Hcl.Block parseRequiredProvidersBlockForTerraform(HclParser parser,
                                                                       String providerName,
                                                                       @Nullable String providerSource,
                                                                       String providerVersion) {
                Hcl.Block terraformBlock = parseTerraformBlock(parser, providerName, providerSource, providerVersion);
                Hcl.Block requiredProvidersBlock = ProviderRecipeSupport.findNestedBlock(terraformBlock, "required_providers");
                if (requiredProvidersBlock == null) {
                    throw new IllegalStateException("Generated terraform block does not contain required_providers block");
                }
                return requiredProvidersBlock;
            }


            private Hcl.Block parseRequiredProvidersBlock(HclParser parser,
                                                          List<String> renderedEntries,
                                                          String closingIndent) {
                String blockText = "required_providers {\n"
                                   + String.join("\n", renderedEntries) + "\n"
                                   + closingIndent + "}\n";
                return ProviderRecipeSupport.parseSingleBlock(parser, blockText, "required_providers block");
            }


            private Hcl.Block parseProviderBlock(HclParser parser,
                                                 String providerName,
                                                 String providerConfiguration) {
                String normalizedConfiguration = providerConfiguration.trim();
                String blockText = "provider " + TopLevelBlockRecipeSupport.quoteHclString(providerName) + " {\n"
                                   + ProviderRecipeSupport.indentBlockBody(normalizedConfiguration, "  ") + "\n"
                                   + "}\n";
                return ProviderRecipeSupport.parseSingleBlock(parser, blockText, "provider block");
            }

            private String renderProviderEntry(String providerName,
                                               @Nullable String providerSource,
                                               String providerVersion,
                                               String indent) {
                String quotedVersion = TopLevelBlockRecipeSupport.quoteHclString(providerVersion);
                if (providerSource == null) {
                    return indent + providerName + " = " + quotedVersion;
                }

                String quotedSource = TopLevelBlockRecipeSupport.quoteHclString(providerSource);
                String nestedIndent = indent + "  ";
                return indent + providerName + " = {\n"
                       + nestedIndent + "source  = " + quotedSource + "\n"
                       + nestedIndent + "version = " + quotedVersion + "\n"
                       + indent + "}";
            }
        });
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateRequiredHclIdentifier(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "providerSource", providerSource);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "providerVersion", providerVersion);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "configuration", configuration);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "source", source);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "version", version);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "moduleName", moduleName);
        return validated;
    }
}

