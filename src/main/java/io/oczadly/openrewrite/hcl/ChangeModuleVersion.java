package io.oczadly.openrewrite.hcl;

import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Validated;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.Hcl;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeModuleVersion extends ModuleRecipe {

    @Option(displayName = "New version", description = "The new version to set for the module")
    String newVersion;

    public ChangeModuleVersion(@Nullable String moduleName,
                               @Nullable String source,
                               String version,
                               String newVersion,
                               @Nullable String filePattern) {
        super(moduleName, source, version, filePattern);
        this.newVersion = newVersion;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Change module version";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Changes the version of an OpenTofu module.";
    }

    @Override
    protected HclVisitor<ExecutionContext> createModuleVisitor() {
        return new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitBlock(Hcl.@NonNull Block block, ExecutionContext ctx) {
                block = (Hcl.Block) super.visitBlock(block, ctx);

                if (matchesModule(block)) {
                    Hcl.Block modified = changeModuleVersion(block);
                    doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                    return modified;
                }

                return block;
            }
        };
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();

        if (version == null || version.trim().isEmpty()) {
            validated = validated.and(Validated.invalid(
                "version",
                version,
                "'version' must be specified and cannot be empty."
            ));
        }

        if (newVersion.trim().isEmpty()) {
            validated = validated.and(Validated.invalid(
                "newVersion",
                newVersion,
                "'newVersion' must be specified and cannot be empty."
            ));
        }

        return validated;
    }

    private Hcl.Block changeModuleVersion(Hcl.Block block) {
        String currentVersion = block.getAttributeValue("version");

        if (currentVersion == null) {
            return block;
        }

        // Remove quotes if present
        currentVersion = ModuleBlockPredicates.removeQuotes(currentVersion);

        if (!version.equals(currentVersion)) {
            return block;
        }

        // Use the built-in method from Hcl.Block
        return block.withAttributeValue("version", newVersion);
    }
}
