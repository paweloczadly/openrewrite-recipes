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
public class AddImportBlock extends Recipe {

    @Option(displayName = "Module name", description = "Optional module block name filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String moduleName;

    @Option(displayName = "Source", description = "Optional module source filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String source;

    @Option(displayName = "Version", description = "Optional module semantic version constraint filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String version;

    @Option(displayName = "To", description = "Resource reference to import into state; supports placeholder interpolation like ${property} and ${property:default}")
    @Nullable
    String to;

    @Option(displayName = "Id", description = "Resource ID to import; supports placeholder interpolation like ${property} and ${property:default}")
    @Nullable
    String id;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false)
    @Nullable
    String filePattern;

    @JsonCreator
    public AddImportBlock(@JsonProperty("moduleName") @Nullable String moduleName,
                          @JsonProperty("source") @Nullable String source,
                          @JsonProperty("version") @Nullable String version,
                          @JsonProperty("to") @Nullable String to,
                          @JsonProperty("id") @Nullable String id,
                          @JsonProperty("filePattern") @Nullable String filePattern) {
        this.moduleName = moduleName;
        this.source = source;
        this.version = version;
        this.to = to;
        this.id = id;
        this.filePattern = filePattern;
    }

    public AddImportBlock(@Nullable String to,
                          @Nullable String id,
                          @Nullable String filePattern) {
        this(null, null, null, to, id, filePattern);
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Add import block";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Adds a top-level import block with configurable to and id fields.";
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return TopLevelBlockRecipeSupport.topLevelBlockVisitor("import", buildBlockBody(), moduleName, source, version, filePattern);
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "moduleName", moduleName);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "source", source);
        validated = TopLevelBlockRecipeSupport.validateOptionalVersionConstraint(validated, version);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "to", to);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "id", id);
        return validated;
    }

    private String buildBlockBody() {
        String resolvedTo = TopLevelBlockRecipeSupport.resolveRequiredValue(to, "to");
        TopLevelBlockRecipeSupport.validateHclTraversal(resolvedTo, "to");
        String resolvedId = TopLevelBlockRecipeSupport.resolveRequiredValue(id, "id");

        return "to = " + resolvedTo + "\n" +
               "id = " + TopLevelBlockRecipeSupport.quoteHclString(resolvedId);
    }
}

