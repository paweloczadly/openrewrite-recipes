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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddRemovedBlock extends Recipe {

    @Option(displayName = "Module name", description = "Optional module block name filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String moduleName;

    @Option(displayName = "Source", description = "Optional module source filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String source;

    @Option(displayName = "Version", description = "Optional module semantic version constraint filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String version;

    @Option(displayName = "From", description = "Resource reference to remove from state; supports placeholder interpolation like ${property} and ${property:default}")
    @Nullable
    String from;

    @Option(displayName = "Lifecycle destroy", description = "Value for lifecycle.destroy inside removed block", required = false)
    @Nullable
    Boolean lifecycleDestroy;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false)
    @Nullable
    String filePattern;

    @JsonCreator
    public AddRemovedBlock(@JsonProperty("moduleName") @Nullable String moduleName,
                           @JsonProperty("source") @Nullable String source,
                           @JsonProperty("version") @Nullable String version,
                           @JsonProperty("from") @Nullable String from,
                           @JsonProperty("lifecycleDestroy") @Nullable Boolean lifecycleDestroy,
                           @JsonProperty("filePattern") @Nullable String filePattern) {
        this.moduleName = moduleName;
        this.source = source;
        this.version = version;
        this.from = from;
        this.lifecycleDestroy = lifecycleDestroy;
        this.filePattern = filePattern;
    }

    public AddRemovedBlock(@Nullable String from,
                           @Nullable Boolean lifecycleDestroy,
                           @Nullable String filePattern) {
        this(null, null, null, from, lifecycleDestroy, filePattern);
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Add removed block";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Adds a top-level removed block with configurable from and lifecycle.destroy fields.";
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return TopLevelBlockRecipeSupport.topLevelBlockVisitor("removed", buildBlockBody(), moduleName, source, version, filePattern);
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "moduleName", moduleName);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "source", source);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "version", version);
        validated = TopLevelBlockRecipeSupport.validateOptionalVersionConstraint(validated, version);
        return TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "from", from);
    }

    private String buildBlockBody() {
        String resolvedFrom = TopLevelBlockRecipeSupport.resolveRequiredValue(from, "from");
        TopLevelBlockRecipeSupport.validateHclTraversal(resolvedFrom, "from");
        boolean destroyValue = lifecycleDestroy != null ? lifecycleDestroy : false;

        return "from = " + resolvedFrom + "\n" +
               "lifecycle {\n" +
               "  destroy = " + destroyValue + "\n" +
               "}";
    }
}

