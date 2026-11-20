package io.oczadly.openrewrite.hcl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.oczadly.openrewrite.hcl.utils.VersionConstraintMatcher;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.tree.Expression;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.Space;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;

import java.util.Collections;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeProviderVersion extends Recipe {

    // Terraform gives a provider entry without a source the source hashicorp/<name>.
    private static final String IMPLIED_SOURCE_NAMESPACE = "hashicorp/";

    @Option(displayName = "Provider name", description = "Optional provider local name filter, i.e. the key in required_providers (for example 'aws')", required = false)
    @Nullable
    String providerName;

    @Option(displayName = "Provider source", description = "The provider source address to match exactly (for example 'hashicorp/aws')")
    @Nullable
    String providerSource;

    @Option(displayName = "Provider version", description = "Optional semantic version constraint filter for the provider's current version constraint", required = false)
    @Nullable
    String providerVersion;

    @Option(displayName = "New version", description = "The new version constraint to set for the provider (for example '~> 6.0')")
    @Nullable
    String newVersion;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false, example = "**/*.tf")
    @Nullable
    String filePattern;

    @JsonCreator
    public ChangeProviderVersion(@JsonProperty("providerName") @Nullable String providerName,
                                 @JsonProperty("providerSource") @Nullable String providerSource,
                                 @JsonProperty("providerVersion") @Nullable String providerVersion,
                                 @JsonProperty("newVersion") @Nullable String newVersion,
                                 @JsonProperty("filePattern") @Nullable String filePattern) {
        this.providerName = providerName;
        this.providerSource = providerSource;
        this.providerVersion = providerVersion;
        this.newVersion = newVersion;
        this.filePattern = filePattern;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Change provider version";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Changes the version constraint of a provider entry in terraform.required_providers.";
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        String resolvedProviderName = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(providerName, "providerName");
        String resolvedProviderSource = TopLevelBlockRecipeSupport.resolveRequiredValue(providerSource, "providerSource");
        String resolvedProviderVersion = TopLevelBlockRecipeSupport.resolveOptionalVersionFilterValue(providerVersion, "providerVersion");
        String resolvedNewVersion = TopLevelBlockRecipeSupport.resolveRequiredValue(newVersion, "newVersion");

        return ProviderRecipeSupport.scopedVisitor(filePattern, new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitAttribute(Hcl.@NonNull Attribute attribute, ExecutionContext ctx) {
                Hcl.Attribute visited = (Hcl.Attribute) super.visitAttribute(attribute, ctx);

                if (!isRequiredProvidersEntry(getCursor())) {
                    return visited;
                }
                if (resolvedProviderName != null && !resolvedProviderName.equals(visited.getSimpleName())) {
                    return visited;
                }

                // A shorthand entry such as `aws = "~> 5.0"` holds only the version.
                if (!(visited.getValue() instanceof Hcl.ObjectValue)) {
                    if (!resolvedProviderSource.equals(impliedSource(visited)) || !shouldChangeVersion(visited)) {
                        return visited;
                    }
                    return withNewVersion(visited);
                }

                Hcl.ObjectValue providerEntry = (Hcl.ObjectValue) visited.getValue();
                Hcl.Attribute sourceAttribute = findEntryAttribute(providerEntry, "source");
                String entrySource = sourceAttribute == null ? impliedSource(visited) : stringValueOf(sourceAttribute);
                if (!resolvedProviderSource.equals(entrySource)) {
                    return visited;
                }

                Hcl.Attribute versionAttribute = findEntryAttribute(providerEntry, "version");
                if (versionAttribute == null || !shouldChangeVersion(versionAttribute)) {
                    return visited;
                }

                Hcl.Attribute updatedVersionAttribute = withNewVersion(versionAttribute);
                // Trees are immutable, so the attribute to replace is the same instance found above.
                return visited.withValue(providerEntry.withArguments(ListUtils.map(
                    providerEntry.getAttributes(),
                    entryAttribute -> entryAttribute == versionAttribute ? updatedVersionAttribute : entryAttribute)));
            }

            /**
             * @param attributeHoldingVersion the {@code version} attribute of an object entry, or a whole shorthand entry
             */
            private boolean shouldChangeVersion(Hcl.Attribute attributeHoldingVersion) {
                String currentVersion = stringValueOf(attributeHoldingVersion);
                return currentVersion != null
                       && !resolvedNewVersion.equals(currentVersion)
                       && (resolvedProviderVersion == null || VersionConstraintMatcher.matches(resolvedProviderVersion, currentVersion));
            }

            /**
             * Only called after {@link #shouldChangeVersion} accepted the attribute, so its value is a plain string.
             */
            private Hcl.Attribute withNewVersion(Hcl.Attribute attributeHoldingVersion) {
                return attributeHoldingVersion.withValue(withStringValue(attributeHoldingVersion.getValue(), resolvedNewVersion));
            }
        });
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateProviderLocalName(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "providerSource", providerSource);
        validated = TopLevelBlockRecipeSupport.validateOptionalVersionConstraint(validated, "providerVersion", providerVersion);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "newVersion", newVersion);
        return validated;
    }

    private static String impliedSource(Hcl.Attribute providerEntry) {
        return IMPLIED_SOURCE_NAMESPACE + providerEntry.getSimpleName();
    }

    /**
     * True when the attribute sits directly in a {@code required_providers} block of a top-level {@code terraform} block:
     * attribute → required_providers → terraform → file.
     */
    private static boolean isRequiredProvidersEntry(Cursor attributeCursor) {
        Cursor requiredProvidersCursor = attributeCursor.getParentTreeCursor();
        if (!(requiredProvidersCursor.getValue() instanceof Hcl.Block)
            || !ProviderRecipeSupport.isBlockType(requiredProvidersCursor.getValue(), "required_providers")) {
            return false;
        }

        Cursor terraformCursor = requiredProvidersCursor.getParentTreeCursor();
        return terraformCursor.getValue() instanceof Hcl.Block
               && ProviderRecipeSupport.isBlockType(terraformCursor.getValue(), "terraform")
               && terraformCursor.getParentTreeCursor().getValue() instanceof Hcl.ConfigFile;
    }

    private static Hcl.@Nullable Attribute findEntryAttribute(Hcl.ObjectValue providerEntry, String name) {
        for (Expression expression : providerEntry.getAttributes()) {
            // A quoted key, such as `"version" = "~> 5.0"`, has no simple name, so its entry is skipped.
            if (expression instanceof Hcl.Attribute
                && ((Hcl.Attribute) expression).getName() instanceof Hcl.Identifier
                && name.equals(((Hcl.Attribute) expression).getSimpleName())) {
                return (Hcl.Attribute) expression;
            }
        }
        return null;
    }

    /**
     * Returns the value of a plain string attribute, or {@code null} when the attribute is missing
     * or its value is not a static string (for example an interpolated template or a variable reference).
     *
     * <p>The parser gives a quoted string as a {@link Hcl.QuotedTemplate} holding one {@link Hcl.Literal} with the
     * text between the quotes.
     */
    private static @Nullable String stringValueOf(Hcl.@Nullable Attribute attribute) {
        if (attribute == null) {
            return null;
        }

        Expression value = attribute.getValue();
        if (value instanceof Hcl.QuotedTemplate) {
            Hcl.QuotedTemplate template = (Hcl.QuotedTemplate) value;
            if (template.getExpressions().isEmpty()) {
                return "";
            }
            if (template.getExpressions().size() == 1 && template.getExpressions().get(0) instanceof Hcl.Literal) {
                return ((Hcl.Literal) template.getExpressions().get(0)).getValueSource();
            }
        }
        return null;
    }

    /**
     * Replaces a value that {@link #stringValueOf} read as a plain string, a {@link Hcl.QuotedTemplate} holding one
     * {@link Hcl.Literal}, or none for {@code ""}. The literal is kept, with its id and markers.
     */
    private static Expression withStringValue(Expression value, String newValue) {
        String quoted = TopLevelBlockRecipeSupport.quoteHclString(newValue);
        // The template's literal holds the escaped text without its quotes.
        String unquoted = quoted.substring(1, quoted.length() - 1);
        Hcl.QuotedTemplate template = (Hcl.QuotedTemplate) value;
        Hcl.Literal literal = template.getExpressions().isEmpty()
            ? new Hcl.Literal(Tree.randomId(), Space.EMPTY, Markers.EMPTY, newValue, unquoted)
            : ((Hcl.Literal) template.getExpressions().get(0)).withValue(newValue).withValueSource(unquoted);
        return template.withExpressions(Collections.singletonList(literal));
    }
}
