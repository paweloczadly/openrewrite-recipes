package io.oczadly.openrewrite.hcl;

import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import io.oczadly.openrewrite.hcl.utils.PropertyPlaceholderResolver;
import io.oczadly.openrewrite.hcl.utils.VersionConstraintMatcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.Label;
import org.openrewrite.hcl.tree.Space;
import org.openrewrite.tree.ParseError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TopLevelBlockRecipeSupport {

    private static final String DEFAULT_FILE_PATTERN = "**/*.tf";

    private TopLevelBlockRecipeSupport() {
    }


    static TreeVisitor<?, ExecutionContext> topLevelBlockVisitor(String blockType,
                                                                 String blockBody,
                                                                 @Nullable String moduleName,
                                                                 @Nullable String source,
                                                                 @Nullable String version,
                                                                 @Nullable String filePattern) {
        // Build one parser per visitor instance and reuse it for all parses within this run.
        HclParser parser = HclParser.builder().build();
        String normalizedModuleName = resolveOptionalFilterValue(moduleName, "moduleName");
        String normalizedSource = resolveOptionalFilterValue(source, "source");
        String normalizedVersion = resolveOptionalVersionFilterValue(version);
        boolean hasModuleFilter = normalizedModuleName != null || normalizedSource != null || normalizedVersion != null;
        // Render once and pre-parse a candidate block for idempotency checks.
        String blockText = renderBlockText(blockType, blockBody);
        Hcl.Block candidateBlock = parseBlock(parser, blockText);

        return Preconditions.check(
            new FindSourceFiles(filePattern != null ? filePattern : DEFAULT_FILE_PATTERN),
            new HclVisitor<ExecutionContext>() {
                @Override
                public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                    Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);

                    if (hasModuleFilter && !containsMatchingModule(visited, normalizedModuleName, normalizedSource, normalizedVersion)) {
                        return visited;
                    }

                    if (containsEquivalentBlock(visited, candidateBlock)) {
                        return visited;
                    }

                    // Create a fresh block instance for each insertion to keep Tree IDs unique across files.
                    Hcl.Block blockToAdd = parseBlock(parser, blockText).withPrefix(Space.EMPTY);
                    List<BodyContent> newBody = new ArrayList<>(visited.getBody());
                    if (!newBody.isEmpty()) {
                        blockToAdd = blockToAdd.withPrefix(Space.format("\n\n"));
                    }
                    newBody.add(blockToAdd);

                    Hcl.ConfigFile modified = visited.withBody(newBody);
                    doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                    return modified;
                }

                private boolean containsEquivalentBlock(Hcl.ConfigFile configFile,
                                                        Hcl.Block candidateBlock) {
                    String candidateTypeName = blockTypeName(candidateBlock);
                    String candidateSignature = blockSignature(candidateBlock);

                    for (BodyContent bodyContent : configFile.getBody()) {
                        if (!(bodyContent instanceof Hcl.Block)) {
                            continue;
                        }

                        Hcl.Block existingBlock = (Hcl.Block) bodyContent;
                        String existingTypeName = blockTypeName(existingBlock);
                        if (!existingTypeName.equals(candidateTypeName)) {
                            continue;
                        }

                        String existingSignature = blockSignature(existingBlock);
                        if (existingSignature.equals(candidateSignature)) {
                            return true;
                        }
                    }

                    return false;
                }

                private String blockTypeName(Hcl.Block block) {
                    Hcl.Identifier type = block.getType();
                    return type == null ? "" : type.getName().toLowerCase(Locale.ROOT);
                }

                private String blockSignature(Hcl.Block block) {
                    // For idempotency, we compare blocks by collecting their normalized attributes
                    // in a map to handle different ordering while preserving semantic meaning.
                    Map<String, String> normalizedAttributes = new LinkedHashMap<>();
                    List<String> nestedBlocks = new ArrayList<>();

                    for (BodyContent bodyContent : block.getBody()) {
                        if (bodyContent instanceof Hcl.Attribute) {
                            Hcl.Attribute attribute = (Hcl.Attribute) bodyContent;
                            String attrName = attribute.getSimpleName().toLowerCase(Locale.ROOT);
                            String attrValue = normalizeHcl(attribute.getValue().printTrimmed(getCursor()));
                            normalizedAttributes.put(attrName, attrValue);
                        } else if (bodyContent instanceof Hcl.Block) {
                            nestedBlocks.add(blockSignature((Hcl.Block) bodyContent));
                        } else {
                            nestedBlocks.add("o:" + normalizeHcl(bodyContent.printTrimmed(getCursor())));
                        }
                    }

                    List<String> labelSignatures = new ArrayList<>(block.getLabels().size());
                    for (Label label : block.getLabels()) {
                        labelSignatures.add(normalizeHcl(label.printTrimmed(getCursor())));
                    }

                    // Build signature: sort nested blocks and attributes for comparison
                    Collections.sort(nestedBlocks);
                    List<String> sortedAttrs = new ArrayList<>();
                    for (Map.Entry<String, String> entry : normalizedAttributes.entrySet()) {
                        sortedAttrs.add("a:" + entry.getKey() + "=" + entry.getValue());
                    }
                    Collections.sort(sortedAttrs);

                    List<String> allBodySignatures = new ArrayList<>(sortedAttrs);
                    allBodySignatures.addAll(nestedBlocks);

                    return "b:"
                           + blockTypeName(block)
                           + "|labels="
                           + String.join(",", labelSignatures)
                           + "|body="
                           + String.join(";", allBodySignatures);
                }

            }
        );
    }

    static @Nullable String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static Validated<Object> validateRequiredNonBlank(Validated<Object> validated,
                                                      String fieldName,
                                                      @Nullable String value) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null) {
            return validated.and(Validated.invalid(
                fieldName,
                value,
                "'" + fieldName + "' must be specified and cannot be empty."
            ));
        }
        return validated;
    }

    static Validated<Object> validateOptionalNonBlank(Validated<Object> validated,
                                                      String fieldName,
                                                      @Nullable String value) {
        if (value != null && value.trim().isEmpty()) {
            return validated.and(Validated.invalid(
                fieldName,
                value,
                "'" + fieldName + "' cannot be empty when specified."
            ));
        }
        return validated;
    }

    static Validated<Object> validateOptionalVersionConstraint(Validated<Object> validated,
                                                               @Nullable String value) {
        validated = validateOptionalNonBlank(validated, "version", value);
        if (value != null && !value.trim().isEmpty() && !value.contains("${") && !VersionConstraintMatcher.isValidConstraint(value)) {
            return validated.and(Validated.invalid(
                "version",
                value,
                VersionConstraintMatcher.INVALID_CONSTRAINT_MESSAGE
            ));
        }
        return validated;
    }

    static Validated<Object> validateRequiredHclIdentifier(Validated<Object> validated,
                                                           String fieldName,
                                                           @Nullable String value) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null || normalizedValue.contains("${")) {
            return validated;
        }
        if (!normalizedValue.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return validated.and(Validated.invalid(
                fieldName,
                value,
                "'" + fieldName + "' must be a valid HCL identifier matching [A-Za-z_][A-Za-z0-9_]*."
            ));
        }
        return validated;
    }

    static String resolveRequiredValue(@Nullable String value, String fieldName) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null) {
            throw new IllegalStateException(
                "'" + fieldName + "' must be specified and cannot be empty."
            );
        }

        if (!normalizedValue.contains("${")) {
            return normalizedValue;
        }

        String resolvedPlaceholder = PropertyPlaceholderResolver.resolve(normalizedValue);
        String normalizedResolved = normalizeNullable(resolvedPlaceholder);
        if (normalizedResolved == null) {
            throw new IllegalStateException("Placeholder '" + normalizedValue + "' for '" + fieldName + "' resolved to an empty or blank value");
        }

        return normalizedResolved;
    }


    static @Nullable String resolveOptionalFilterValue(@Nullable String value, String fieldName) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null) {
            return null;
        }

        if (!normalizedValue.contains("${")) {
            return normalizedValue;
        }

        String resolved = PropertyPlaceholderResolver.resolve(normalizedValue);
        String normalizedResolved = normalizeNullable(resolved);
        if (normalizedResolved == null) {
            throw new IllegalStateException(
                "Placeholder '" + normalizedValue + "' for '" + fieldName + "' resolved to an empty or blank value"
            );
        }

        return normalizedResolved;
    }

    static @Nullable String resolveOptionalVersionFilterValue(@Nullable String value) {
        String resolved = resolveOptionalFilterValue(value, "version");
        if (resolved != null && !VersionConstraintMatcher.isValidConstraint(resolved)) {
            throw new IllegalStateException(VersionConstraintMatcher.INVALID_CONSTRAINT_MESSAGE);
        }
        return resolved;
    }

    static boolean containsMatchingModule(Hcl.ConfigFile configFile,
                                          @Nullable String moduleName,
                                          @Nullable String source,
                                          @Nullable String version) {
        for (BodyContent bodyContent : configFile.getBody()) {
            if (!(bodyContent instanceof Hcl.Block)) {
                continue;
            }

            if (matchesModuleFilters((Hcl.Block) bodyContent, moduleName, source, version)) {
                return true;
            }
        }

        return false;
    }

    static boolean matchesModuleFilters(Hcl.Block block,
                                        @Nullable String moduleName,
                                        @Nullable String source,
                                        @Nullable String version) {
        Hcl.Identifier type = block.getType();
        if (type == null || !"module".equalsIgnoreCase(type.getName())) {
            return false;
        }
        if (!ModuleBlockPredicates.matchesModuleName(block, moduleName)) {
            return false;
        }
        if (source != null && !source.equals(ModuleBlockPredicates.getAttributeValue(block, "source"))) {
            return false;
        }
        return version == null || VersionConstraintMatcher.matches(version, ModuleBlockPredicates.getAttributeValue(block, "version"));
    }

    static String quoteHclString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return "\"" + escaped + "\"";
    }


    /**
     * Validates that a resolved value is a well-formed HCL traversal reference suitable for
     * embedding unquoted as a {@code from} / {@code to} address in {@code moved}, {@code removed},
     * or {@code import} blocks.
     *
     * <p>Rejects values that:
     * <ul>
     *   <li>contain line breaks (would produce multi-line HCL)</li>
     *   <li>contain comment syntax ({@code #}, {@code //}, {@code /*}) outside quoted string literals</li>
     *   <li>start with a double-quote character (a quoted string literal is not a traversal reference)</li>
     *   <li>do not start with a letter or underscore (HCL identifiers must begin with {@code [a-zA-Z_]})</li>
     * </ul>
     *
     * <p>Valid examples: {@code module.foo}, {@code azurerm_resource_group.rg},
     * {@code module.vnet.module.subnet["key"].resource[0]}.
     *
     * @throws IllegalStateException if the value is not a valid HCL traversal reference
     */
    static void validateHclTraversal(String value, String fieldName) {
        if (value.contains("\n") || value.contains("\r")) {
            throw new IllegalStateException(
                "'" + fieldName + "' must be a single-line HCL traversal reference but contains a line break: " + value
            );
        }
        if (containsCommentSyntaxOutsideStrings(value)) {
            throw new IllegalStateException(
                "'" + fieldName + "' must be a single-line HCL traversal reference but contains comment syntax: " + value
            );
        }
        if (!value.isEmpty() && value.charAt(0) == '"') {
            throw new IllegalStateException(
                "'" + fieldName + "' must be an HCL traversal reference (e.g. module.foo) but looks like a quoted string literal: " + value
            );
        }
        if (value.isEmpty() || !Character.isLetter(value.charAt(0)) && value.charAt(0) != '_') {
            throw new IllegalStateException(
                "'" + fieldName + "' must be an HCL traversal reference starting with a letter or underscore, but got: " + value
            );
        }
    }

    private static boolean containsCommentSyntaxOutsideStrings(String value) {
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '#') {
                return true;
            }

            if (c == '/' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == '/' || next == '*') {
                    return true;
                }
            }
        }

        return false;
    }

    private static Hcl.Block parseBlock(HclParser parser, String blockText) {
        SourceFile sourceFile = parser
            .parse(blockText)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not parse generated top-level block"));

        if (sourceFile instanceof ParseError) {
            throw new IllegalStateException("Could not parse generated top-level block: " + sourceFile);
        }
        if (!(sourceFile instanceof Hcl.ConfigFile)) {
            throw new IllegalStateException("Expected generated HCL to be a ConfigFile but was " + sourceFile.getClass().getSimpleName());
        }

        Hcl.ConfigFile parsed = (Hcl.ConfigFile) sourceFile;
        BodyContent firstBodyItem = parsed.getBody().isEmpty() ? null : parsed.getBody().get(0);
        if (!(firstBodyItem instanceof Hcl.Block)) {
            throw new IllegalStateException("Generated HCL does not contain a top-level block");
        }

        return (Hcl.Block) firstBodyItem;
    }

    /**
     * Normalizes HCL text for idempotency comparison by removing whitespace and comments
     * outside quoted string literals. Whitespace and comment-like content inside strings
     * is preserved so values like {@code "a b"} and {@code "ab"} remain distinct.
     *
     * <p>This implementation carefully handles escape sequences to ensure the normalized
     * output is semantically equivalent to the input.</p>
     */
    private static String normalizeHcl(String hclText) {
        StringBuilder result = new StringBuilder(hclText.length());
        boolean inString = false;
        boolean escaped = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < hclText.length(); i++) {
            char c = hclText.charAt(i);

            // Handle end of line comment
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }

            // Handle end of block comment
            if (inBlockComment) {
                if (c == '*' && i + 1 < hclText.length() && hclText.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++; // Skip the '/'
                }
                continue;
            }

            // Handle escaped character within string
            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }

            // Detect escape sequence start within string
            if (c == '\\' && inString) {
                result.append(c);
                escaped = true;
                continue;
            }

            // Toggle string state
            if (c == '"') {
                inString = !inString;
                result.append(c);
                continue;
            }

            // Detect comments only outside strings
            if (!inString) {
                if (c == '#') {
                    inLineComment = true;
                    continue;
                }
                if (c == '/' && i + 1 < hclText.length()) {
                    char next = hclText.charAt(i + 1);
                    if (next == '/') {
                        inLineComment = true;
                        i++; // Skip the second '/'
                        continue;
                    }
                    if (next == '*') {
                        inBlockComment = true;
                        i++; // Skip the '*'
                        continue;
                    }
                }
            }

            // Skip whitespace outside strings
            if (!inString && Character.isWhitespace(c)) {
                continue;
            }

            result.append(c);
        }

        return result.toString().trim();
    }

    private static String renderBlockText(String rawBlockType, String rawBlockBody) {
        String normalizedType = rawBlockType.toLowerCase(Locale.ROOT).trim();
        String normalizedBody = rawBlockBody.trim();
        String[] lines = normalizedBody.split("\\R");
        StringBuilder indentedBody = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                indentedBody.append('\n');
            }
            indentedBody.append("  ").append(lines[i]);
        }

        return normalizedType + " {\n" + indentedBody + "\n}\n";
    }
}
