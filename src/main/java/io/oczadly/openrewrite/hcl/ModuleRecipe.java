package io.oczadly.openrewrite.hcl;

import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.tree.Hcl;

/**
 * Base class for recipes that modify OpenTofu module blocks.
 *
 * <p>Implementations must provide:
 * <ul>
 *   <li>Display name and description via {@link #getDisplayName()} and {@link #getDescription()}</li>
 *   <li>Module-specific visitor logic via {@link #createModuleVisitor()}</li>
 * </ul>
 *
 * <p>Module matching behavior:
 * <ul>
 *   <li>If {@code moduleName} is specified, only modules with matching labels are processed</li>
 *   <li>{@code source} is always required and matches the module's source attribute</li>
 *   <li>{@code version} is optional; if specified, only modules with matching version are processed</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * new ChangeModuleVersion(
 *     "my-module",                              // moduleName (optional)
 *     "Azure/avm-res-network-virtualnetwork",   // source (required)
 *     ">= 0.10.0",                              // oldVersion (optional)
 *     "0.11.0",                                 // newVersion
 *     "**\/*.tf"                                 // filePattern (optional)
 * )
 * }</pre>
 *
 * @see ModuleBlockPredicates for matching logic
 */
public abstract class ModuleRecipe extends Recipe {

    private static final String DEFAULT_FILE_PATTERN = "**/*.tf";

    @Nullable
    @Option(displayName = "Module name", description = "The name of the module block to modify", required = false)
    final String moduleName;

    @NonNull
    @Option(displayName = "Source", description = "The source address of the module block to modify. Can reference local modules or remote registry modules", required = true)
    final String source;

    @Nullable
    @Option(displayName = "Version", description = "The version of the module block to modify", required = false)
    final String version;

    @Nullable
    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false, example = DEFAULT_FILE_PATTERN)
    final String filePattern;

    protected ModuleRecipe(@Nullable String moduleName,
                           String source,
                           @Nullable String version,
                           @Nullable String filePattern) {
        this.moduleName = moduleName;
        this.source = source;
        this.version = version;
        this.filePattern = filePattern;
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(filePattern != null ? filePattern : DEFAULT_FILE_PATTERN), createModuleVisitor());
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();

        if (source == null || source.trim().isEmpty()) {
            validated = validated.and(Validated.invalid(
                "source",
                source,
                "'source' is required and cannot be empty."
            ));
            return validated;
        }

        return validated;
    }

    protected abstract HclVisitor<ExecutionContext> createModuleVisitor();

    protected boolean matchesModule(Hcl.Block block) {
        if (!ModuleBlockPredicates.matchesModuleName(block, moduleName)) {
            return false;
        }

        if (source != null && !source.equals(ModuleBlockPredicates.getAttributeValue(block, "source"))) {
            return false;
        }

        return version == null || version.equals(ModuleBlockPredicates.getAttributeValue(block, "version"));
    }
}
