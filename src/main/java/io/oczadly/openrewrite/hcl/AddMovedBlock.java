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
public class AddMovedBlock extends Recipe {

    @Option(displayName = "Module name", description = "Optional module block name filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String moduleName;

    @Option(displayName = "Source", description = "Optional module source filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String source;

    @Option(displayName = "Version", description = "Optional module version filter; recipe applies only in files containing a matching module", required = false)
    @Nullable
    String version;

    @Option(displayName = "From", description = "Old resource reference to move from; supports placeholder interpolation like ${property} and ${property:default}")
    @Nullable
    String from;

    @Option(displayName = "To", description = "New resource reference to move to; supports placeholder interpolation like ${property} and ${property:default}")
    @Nullable
    String to;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false)
    @Nullable
    String filePattern;

    @JsonCreator
    public AddMovedBlock(@JsonProperty("moduleName") @Nullable String moduleName,
                         @JsonProperty("source") @Nullable String source,
                         @JsonProperty("version") @Nullable String version,
                         @JsonProperty("from") @Nullable String from,
                         @JsonProperty("to") @Nullable String to,
                         @JsonProperty("filePattern") @Nullable String filePattern) {
        this.moduleName = moduleName;
        this.source = source;
        this.version = version;
        this.from = from;
        this.to = to;
        this.filePattern = filePattern;
    }

    public AddMovedBlock(@Nullable String from,
                         @Nullable String to,
                         @Nullable String filePattern) {
        this(null, null, null, from, to, filePattern);
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Add moved block";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Adds a top-level moved block with configurable from and to fields.";
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return TopLevelBlockRecipeSupport.topLevelBlockVisitor("moved", buildBlockBody(), moduleName, source, version, filePattern);
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "moduleName", moduleName);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "source", source);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "version", version);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "from", from);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "to", to);
        return validated;
    }

    private String buildBlockBody() {
        String resolvedFrom = TopLevelBlockRecipeSupport.resolveRequiredValue(from, "from");
        TopLevelBlockRecipeSupport.validateHclTraversal(resolvedFrom, "from");
        String resolvedTo = TopLevelBlockRecipeSupport.resolveRequiredValue(to, "to");
        TopLevelBlockRecipeSupport.validateHclTraversal(resolvedTo, "to");

        return "from = " + resolvedFrom + "\n" +
               "to   = " + resolvedTo;
    }
}

