package io.oczadly.openrewrite.hcl.utils;

import org.jspecify.annotations.Nullable;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Hcl;

/**
 * Utility class providing predicates and helper methods for working with Terraform module blocks in HCL.
 */
public class ModuleBlockPredicates {

    private static final String MODULE_BLOCK_TYPE = "module";

    private static final String QUOTE_REGEX = "^\"|\"$";

    /**
     * Checks if the HCL block is a {@code module} block with the specified name.
     * <p>
     *     A block matches when:
     *     <ul>
     *         <li>It's a {@code module} block</li>
     *         <li>It has at least one label</li>
     *         <li>The first label (module name) matches {moduleName}</li>
     *     </ul>
     * </p>
     *
     * @param block the HCL block to check
     * @return {@code true} if the block matches, {@code false} otherwise
     */
    public static boolean matchesModuleName(Hcl.Block block, @Nullable String moduleName) {
        if (moduleName == null) return true;

        return block.getLabels().stream()
            .anyMatch(label -> {
                if (label instanceof Hcl.QuotedTemplate) {
                    return ((Hcl.QuotedTemplate) label).getExpressions().stream()
                        .anyMatch(e -> e instanceof Hcl.Literal &&
                            ((Hcl.Literal) e).getValueSource().equals("\"" + moduleName + "\""));
                } else if (label instanceof Hcl.Literal) {
                    String value = ((Hcl.Literal) label).getValueSource();
                    return value.equals("\"" + moduleName + "\"") || value.equals(moduleName);
                }
                return false;
            });
    }


    /**
     * Retrieves the value of the specified attribute from the given HCL block.
     *
     * @param block         the HCL block to search
     * @param attributeName the name of the attribute to retrieve
     * @return the attribute value as a string, or {@code null} if not found
     */
    public static @Nullable String getAttributeValue(Hcl.Block block, String attributeName) {
        for (BodyContent content : block.getBody()) {
            if (content instanceof Hcl.Attribute) {
                Hcl.Attribute attribute = (Hcl.Attribute) content;
                if (attribute.getSimpleName().equals(attributeName)) {
                    String value = block.getAttributeValue(attributeName);
                    return value != null ? removeQuotes(value) : null;
                }
            }
        }
        return null;
    }

    /**
     * Removes surrounding quotes from a string value.
     * @param value the string value
     * @return the unquoted string
     */
    public static String removeQuotes(String value) {
        return value.replaceAll(QUOTE_REGEX, "");
    }

    /**
     * Detects the indentation style used in the given HCL block.
     *
     * @param block the HCL block to analyze
     * @return the detected indentation string (e.g., spaces or tabs)
     */
    public static String detectIndentation(Hcl.Block block) {
        for (BodyContent content : block.getBody()) {
            if (content instanceof Hcl.Attribute) {
                Hcl.Attribute attribute = (Hcl.Attribute) content;

                String prefix = attribute.getPrefix().getWhitespace();
                int lastNewline = prefix.lastIndexOf('\n');
                if (lastNewline >= 0) {
                    return prefix.substring(lastNewline + 1);
                }
            }
        }
        return "  "; // Default to two spaces if no attributes are present
    }

    private ModuleBlockPredicates() {}
}
