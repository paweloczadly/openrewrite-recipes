package io.oczadly.openrewrite.hcl;

import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import io.oczadly.openrewrite.hcl.utils.PropertyPlaceholderResolver;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Expression;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.tree.ParseError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Pattern;

/**
 * Transforms values inside Terraform/OpenTofu {@code locals} objects using a constrained path syntax.
 *
 * <p>The recipe currently supports record-style paths such as {@code records.*.value} and
 * {@code *.records.*.value} with the following transformations:</p>
 * <ul>
 *     <li>{@code stringToList}: {@code value = "foo"} -> {@code value = ["foo"]}</li>
 *     <li>{@code listToString}: {@code value = ["foo"]} -> {@code value = "foo"}</li>
 * </ul>
 *
 * <p>When module filters are configured, the recipe uses a two-phase scan/apply model: it first
 * finds directories containing matching module blocks and then applies the locals transformation
 * to files in those same directories.</p>
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertLocalValueInPath extends ScanningRecipe<ConvertLocalValueInPath.ScanState> {

    private static final String HCL_IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    @Value
    static class ResolvedOptions {
        @Nullable String source;
        @Nullable String version;
        @Nullable String moduleName;
        String localName;
        String attributePath;
        AttributeTransformation transformation;
        String filePattern;

        boolean hasModuleFilters() {
            return source != null || version != null || moduleName != null;
        }
    }

    @Value
    public static class ScanState {
        Set<String> matchingDirectories = new ConcurrentSkipListSet<>();

        void addMatchingFile(Hcl.ConfigFile configFile) {
            matchingDirectories.add(directoryOf(configFile));
        }

        boolean directoryMatches(Hcl.ConfigFile configFile) {
            return matchingDirectories.contains(directoryOf(configFile));
        }

        private String directoryOf(Hcl.ConfigFile configFile) {
            if (configFile.getSourcePath().getParent() == null) {
                return "";
            }
            return configFile.getSourcePath().getParent().toString();
        }
    }

    private static final String DEFAULT_FILE_PATTERN = "**/*.tf";
    private static final List<String> SUPPORTED_ATTRIBUTE_PATHS = Arrays.asList("records.*.value", "*.records.*.value");
    private static final Set<String> SUPPORTED_PATHS = new LinkedHashSet<>(SUPPORTED_ATTRIBUTE_PATHS);
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z0-9])([A-Z])");
    // Accept Terraform inline comments in value assignments: #, // and one-line /* ... */.
    private static final String OPTIONAL_INLINE_COMMENT = "(\\s*(?:(?://|#)[^\\r\\n]*|/\\*[^\\r\\n]*?\\*/)?\\s*)";
    private static final Pattern STRING_VALUE_ASSIGNMENT = Pattern.compile(
        "(?m)^(\\s*value\\s*=\\s*)\"((?:[^\"\\\\]|\\\\.)*)\"" + OPTIONAL_INLINE_COMMENT + "$"
    );
    private static final Pattern SINGLE_STRING_LIST_VALUE_ASSIGNMENT = Pattern.compile(
        "(?m)^(\\s*value\\s*=\\s*)\\[\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*]" + OPTIONAL_INLINE_COMMENT + "$"
    );

    @Option(displayName = "Module name",
            description = "Only apply if module is referenced by this name",
            required = false)
    @Nullable
    String moduleName;

    @Option(displayName = "Source",
            description = "Only apply if module source matches exactly",
            required = false)
    @Nullable
    String source;

    @Option(displayName = "Version",
            description = "Only apply if module version matches exactly",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Local variable name",
            description = "Name of the locals variable (e.g., 'txt_records', 'ptr_records')")
    String localName;

    @Option(displayName = "Attribute path",
            description = "Supported values: records.*.value or *.records.*.value")
    String attributePath;

    @Option(displayName = "Transformation type",
            description = "Type of transformation: stringToList, listToString")
    String transformation;

    @Option(displayName = "File pattern",
            description = "Only apply to files matching this glob pattern",
            required = false)
    @Nullable
    String filePattern;

    public ConvertLocalValueInPath(@Nullable String moduleName,
                                       @Nullable String source,
                                       @Nullable String version,
                                       String localName,
                                       String attributePath,
                                       String transformation,
                                       @Nullable String filePattern) {
        this.moduleName = moduleName;
        this.source = source;
        this.version = version;
        this.localName = localName;
        this.attributePath = attributePath;
        this.transformation = transformation;
        this.filePattern = filePattern;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Convert local value in path";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Transform attribute values in nested map structures using path expressions.";
    }

    @Override
    public @NonNull ScanState getInitialValue(@Nullable ExecutionContext ctx) {
        return new ScanState();
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getScanner(@Nullable ScanState acc) {
        ResolvedOptions options = resolveOptionsForScanner();
        if (!options.hasModuleFilters() || acc == null) {
            return TreeVisitor.noop();
        }

        return Preconditions.check(
            new FindSourceFiles(DEFAULT_FILE_PATTERN),
            new HclVisitor<ExecutionContext>() {
                @Override
                public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                    Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);


                    // Mark the directory as eligible for the apply phase when any module block matches.
                    if (matchesModuleFilters(visited, options.source, options.version, options.moduleName)) {
                        acc.addMatchingFile(visited);
                    }
                    return visited;
                }
            }
        );
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor(@Nullable ScanState acc) {
        ResolvedOptions options = resolveOptionsForVisitor();
        if (options == null) {
            return TreeVisitor.noop();
        }
        if (options.hasModuleFilters() && acc == null) {
            return TreeVisitor.noop();
        }
        HclParser parser = HclParser.builder().build();

        return Preconditions.check(
            new FindSourceFiles(options.filePattern),
            new HclVisitor<ExecutionContext>() {
                @Override
                public @NonNull Hcl visitConfigFile(Hcl.@NonNull ConfigFile configFile, ExecutionContext ctx) {
                    Hcl.ConfigFile visited = (Hcl.ConfigFile) super.visitConfigFile(configFile, ctx);

                    // In two-phase mode, module filters are satisfied if any file in the same
                    // directory matched during the scan phase.
                    if (options.hasModuleFilters() && (acc == null || !acc.directoryMatches(visited))) {
                        return visited;
                    }

                    // Process locals block
                    List<BodyContent> newBody = new ArrayList<>(visited.getBody());
                    boolean modified = false;

                    for (int i = 0; i < newBody.size(); i++) {
                        if (newBody.get(i) instanceof Hcl.Block) {
                            Hcl.Block block = (Hcl.Block) newBody.get(i);
                            if ("locals".equals(blockTypeName(block))) {
                                Hcl.Block transformedBlock = transformLocalsBlock(
                                    block,
                                    parser,
                                    options
                                );
                                if (transformedBlock != block) {
                                    newBody.set(i, transformedBlock);
                                    modified = true;
                                }
                            }
                        }
                    }

                    if (!modified) {
                        return visited;
                    }

                    Hcl.ConfigFile result = visited.withBody(newBody);
                    doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, result));
                    return result;
                }

                /**
                 * Rewrites the configured locals attribute when the path/transformation pair is supported.
                 */
                private Hcl.Block transformLocalsBlock(Hcl.Block localsBlock,
                                                       HclParser parser,
                                                       ResolvedOptions options) {
                    if (isUnsupportedTransformationPath(options.attributePath)) {
                        return localsBlock;
                    }

                    List<BodyContent> updatedBody = new ArrayList<>(localsBlock.getBody().size());
                    boolean modified = false;

                    for (BodyContent bodyContent : localsBlock.getBody()) {
                        if (!(bodyContent instanceof Hcl.Attribute)) {
                            updatedBody.add(bodyContent);
                            continue;
                        }

                        Hcl.Attribute attribute = (Hcl.Attribute) bodyContent;
                        if (!options.localName.equals(attribute.getSimpleName())) {
                            updatedBody.add(bodyContent);
                            continue;
                        }

                        String renderedValue = stripLeadingWhitespace(attribute.getValue().print(getCursor()));
                        String transformedValueText = transformValueTextByPath(renderedValue, options);

                        if (renderedValue.equals(transformedValueText)) {
                            updatedBody.add(bodyContent);
                            continue;
                        }

                        Expression transformedExpression = parseAttributeValue(parser, attribute, transformedValueText);
                        updatedBody.add(attribute.withValue(transformedExpression));
                        modified = true;
                    }

                    return modified ? localsBlock.withBody(updatedBody) : localsBlock;
                }

                /**
                 * Re-parses the transformed locals attribute value back into an HCL expression while
                 * preserving the original attribute indentation context.
                 */
                private Expression parseAttributeValue(HclParser parser,
                                                       Hcl.Attribute originalAttribute,
                                                       String valueText) {
                    String attributeName = originalAttribute.getSimpleName();
                    String attributeIndent = indentationOf(originalAttribute.getPrefix().getWhitespace());
                    String snippet = "locals {\n"
                                     + attributeIndent + attributeName + " = " + valueText + "\n"
                                     + "}\n";
                    SourceFile sourceFile = parser.parse(snippet)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Unable to parse transformed locals value."));

                    if (sourceFile instanceof ParseError) {
                        throw new IllegalStateException("Unable to parse transformed locals value: " + sourceFile);
                    }
                    if (!(sourceFile instanceof Hcl.ConfigFile)) {
                        throw new IllegalStateException("Expected parsed snippet to be a ConfigFile.");
                    }

                    Hcl.ConfigFile parsed = (Hcl.ConfigFile) sourceFile;
                    for (BodyContent topLevel : parsed.getBody()) {
                        if (!(topLevel instanceof Hcl.Block)) {
                            continue;
                        }
                        Hcl.Block block = (Hcl.Block) topLevel;
                        if (!"locals".equals(blockTypeName(block))) {
                            continue;
                        }
                        for (BodyContent content : block.getBody()) {
                            if (content instanceof Hcl.Attribute) {
                                Hcl.Attribute attribute = (Hcl.Attribute) content;
                                if (attributeName.equals(attribute.getSimpleName())) {
                                    return attribute.getValue();
                                }
                            }
                        }
                    }

                    throw new IllegalStateException("Transformed locals snippet does not contain target attribute '" + attributeName + "'.");
                }

                private String indentationOf(String whitespace) {
                    int newline = whitespace.lastIndexOf('\n');
                    if (newline >= 0) {
                        return whitespace.substring(newline + 1);
                    }
                    return whitespace;
                }

                private String stripLeadingWhitespace(String text) {
                    int index = 0;
                    while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                        index++;
                    }
                    return text.substring(index);
                }

                /**
                 * Applies the configured transformation only inside {@code records = { ... }} object bodies.
                 */
                private String transformValueTextByPath(String valueText, ResolvedOptions options) {
                    if (isUnsupportedTransformationPath(options.attributePath)) {
                        return valueText;
                    }

                    String attributePath = options.attributePath;

                    StringBuilder rewritten = new StringBuilder(valueText.length() + 32);
                    int cursor = 0;
                    while (true) {
                        int[] recordsMatch = findNextRecordsObjectStart(valueText, cursor, attributePath);
                        if (recordsMatch[0] < 0) {
                            break;
                        }

                        int blockOpen = recordsMatch[1];
                        int blockClose = findMatchingClosingBrace(valueText, blockOpen);
                        if (blockClose < 0) {
                            break;
                        }

                        rewritten.append(valueText, cursor, blockOpen + 1);
                        String recordsBody = valueText.substring(blockOpen + 1, blockClose);
                        String transformedRecordsBody = transformRecordsBody(recordsBody, options.transformation);
                        rewritten.append(transformedRecordsBody);
                        cursor = blockClose;
                    }

                    if (cursor == 0) {
                        return valueText;
                    }
                    rewritten.append(valueText.substring(cursor));
                    return rewritten.toString();
                }

                private int[] findNextRecordsObjectStart(String text, int fromIndex, String attributePath) {
                    boolean inString = false;
                    boolean escaped = false;
                    boolean inLineComment = false;
                    boolean inBlockComment = false;
                    int depth = 0;

                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);

                        if (inLineComment) {
                            if (c == '\n' || c == '\r') {
                                inLineComment = false;
                            }
                            continue;
                        }

                        if (inBlockComment) {
                            if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                                inBlockComment = false;
                                i++;
                            }
                            continue;
                        }

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
                            inLineComment = true;
                            continue;
                        }

                        if (c == '/' && i + 1 < text.length()) {
                            char next = text.charAt(i + 1);
                            if (next == '/') {
                                inLineComment = true;
                                i++;
                                continue;
                            }
                            if (next == '*') {
                                inBlockComment = true;
                                i++;
                                continue;
                            }
                        }

                        if (c == '{') {
                            depth++;
                            continue;
                        }

                        if (c == '}') {
                            depth = Math.max(0, depth - 1);
                            continue;
                        }

                        if (i >= fromIndex && text.startsWith("records", i) && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)) && text.charAt(i - 1) != '_')) {
                            int j = i + "records".length();
                            while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                                j++;
                            }
                            if (j >= text.length() || text.charAt(j) != '=') {
                                continue;
                            }
                            j++;
                            while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                                j++;
                            }
                            if (j < text.length() && text.charAt(j) == '{' && matchesRecordsDepth(attributePath, depth)) {
                                return new int[]{i, j};
                            }
                        }
                    }

                    return new int[]{-1, -1};
                }

                private boolean matchesRecordsDepth(String attributePath, int depth) {
                    if ("*.records.*.value".equals(attributePath)) {
                        return depth == 2;
                    }
                    // Backward-compatible alias used across existing recipes/tests.
                    return depth == 1 || depth == 2;
                }

                private String transformRecordsBody(String recordsBody, AttributeTransformation transformation) {
                    StringBuilder rewritten = new StringBuilder(recordsBody.length() + 16);
                    int cursor = 0;
                    boolean inString = false;
                    boolean escaped = false;
                    boolean inLineComment = false;
                    boolean inBlockComment = false;
                    int depth = 0;

                    for (int i = 0; i < recordsBody.length(); i++) {
                        char c = recordsBody.charAt(i);

                        if (inLineComment) {
                            if (c == '\n' || c == '\r') {
                                inLineComment = false;
                            }
                            continue;
                        }

                        if (inBlockComment) {
                            if (c == '*' && i + 1 < recordsBody.length() && recordsBody.charAt(i + 1) == '/') {
                                inBlockComment = false;
                                i++;
                            }
                            continue;
                        }

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
                            inLineComment = true;
                            continue;
                        }

                        if (c == '/' && i + 1 < recordsBody.length()) {
                            char next = recordsBody.charAt(i + 1);
                            if (next == '/') {
                                inLineComment = true;
                                i++;
                                continue;
                            }
                            if (next == '*') {
                                inBlockComment = true;
                                i++;
                                continue;
                            }
                        }

                        if (c == '{') {
                            if (depth == 0) {
                                int objectClose = findMatchingClosingBrace(recordsBody, i);
                                if (objectClose < 0) {
                                    return recordsBody;
                                }

                                rewritten.append(recordsBody, cursor, i + 1);
                                String objectBody = recordsBody.substring(i + 1, objectClose);
                                rewritten.append(transformImmediateValueAssignments(objectBody, transformation));
                                cursor = objectClose;
                                i = objectClose;
                                continue;
                            }
                            depth++;
                            continue;
                        }

                        if (c == '}') {
                            depth--;
                        }
                    }

                    if (cursor == 0) {
                        return recordsBody;
                    }

                    rewritten.append(recordsBody.substring(cursor));
                    return rewritten.toString();
                }

                private String transformImmediateValueAssignments(String objectBody, AttributeTransformation transformation) {
                    StringBuilder rewritten = new StringBuilder(objectBody.length() + 8);
                    int cursor = 0;
                    boolean inString = false;
                    boolean escaped = false;
                    boolean inLineComment = false;
                    boolean inBlockComment = false;
                    int depth = 0;

                    for (int i = 0; i < objectBody.length(); i++) {
                        char c = objectBody.charAt(i);

                        if (inLineComment) {
                            if (c == '\n' || c == '\r') {
                                inLineComment = false;
                            }
                            continue;
                        }

                        if (inBlockComment) {
                            if (c == '*' && i + 1 < objectBody.length() && objectBody.charAt(i + 1) == '/') {
                                inBlockComment = false;
                                i++;
                            }
                            continue;
                        }

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
                            inLineComment = true;
                            continue;
                        }

                        if (c == '/' && i + 1 < objectBody.length()) {
                            char next = objectBody.charAt(i + 1);
                            if (next == '/') {
                                inLineComment = true;
                                i++;
                                continue;
                            }
                            if (next == '*') {
                                inBlockComment = true;
                                i++;
                                continue;
                            }
                        }

                        if (c == '{') {
                            depth++;
                            continue;
                        }

                        if (c == '}') {
                            depth--;
                            continue;
                        }

                        if (depth == 0 && objectBody.startsWith("value", i)
                            && (i == 0 || !Character.isLetterOrDigit(objectBody.charAt(i - 1)) && objectBody.charAt(i - 1) != '_')) {
                            int lineStart = lineStart(objectBody, i);
                            int lineEnd = lineEnd(objectBody, i);
                            if (lineStart < cursor) {
                                continue;
                            }

                            rewritten.append(objectBody, cursor, lineStart);
                            String originalLine = objectBody.substring(lineStart, lineEnd);
                            rewritten.append(transformValueAssignmentLine(originalLine, transformation));
                            cursor = lineEnd;
                            i = lineEnd - 1;
                        }
                    }

                    if (cursor == 0) {
                        return objectBody;
                    }

                    rewritten.append(objectBody.substring(cursor));
                    return rewritten.toString();
                }

                private String transformValueAssignmentLine(String line, AttributeTransformation transformation) {
                    if (transformation == AttributeTransformation.STRING_TO_LIST) {
                        return STRING_VALUE_ASSIGNMENT.matcher(line)
                            .replaceAll("$1[\"$2\"]$3");
                    }
                    return SINGLE_STRING_LIST_VALUE_ASSIGNMENT.matcher(line)
                        .replaceAll("$1\"$2\"$3");
                }

                private int lineStart(String text, int index) {
                    int lineStart = index;
                    while (lineStart > 0) {
                        char previous = text.charAt(lineStart - 1);
                        if (previous == '\n' || previous == '\r') {
                            break;
                        }
                        lineStart--;
                    }
                    return lineStart;
                }

                private int lineEnd(String text, int index) {
                    int lineEnd = index;
                    while (lineEnd < text.length()) {
                        char current = text.charAt(lineEnd);
                        if (current == '\n' || current == '\r') {
                            break;
                        }
                        lineEnd++;
                    }
                    return lineEnd;
                }

                /**
                 * Finds the matching closing brace for an object literal while respecting quoted
                 * strings and skipping line/block comments outside string literals.
                 */
                private int findMatchingClosingBrace(String text, int openingBraceIndex) {
                    int depth = 0;
                    boolean inString = false;
                    boolean escaped = false;
                    boolean inLineComment = false;
                    boolean inBlockComment = false;

                    for (int i = openingBraceIndex; i < text.length(); i++) {
                        char c = text.charAt(i);

                        if (inLineComment) {
                            if (c == '\n' || c == '\r') {
                                inLineComment = false;
                            }
                            continue;
                        }

                        if (inBlockComment) {
                            if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                                inBlockComment = false;
                                i++;
                            }
                            continue;
                        }

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
                            inLineComment = true;
                            continue;
                        }

                        if (c == '/' && i + 1 < text.length()) {
                            char next = text.charAt(i + 1);
                            if (next == '/') {
                                inLineComment = true;
                                i++;
                                continue;
                            }
                            if (next == '*') {
                                inBlockComment = true;
                                i++;
                                continue;
                            }
                        }

                        if (c == '{') {
                            depth++;
                        } else if (c == '}') {
                            depth--;
                            if (depth == 0) {
                                return i;
                            }
                        }
                    }

                    return -1;
                }

            }
        );
    }

    private ResolvedOptions resolveOptionsForScanner() {
        return new ResolvedOptions(
            resolveOptional(source),
            resolveOptional(version),
            resolveOptional(moduleName),
            localName,
            attributePath,
            AttributeTransformation.STRING_TO_LIST,
            DEFAULT_FILE_PATTERN
        );
    }

    private @Nullable ResolvedOptions resolveOptionsForVisitor() {
        String resolvedTransformation = requireResolvedNonBlank(transformation, "transformation");
        String resolvedAttributePath = requireResolvedNonBlank(attributePath, "attributePath");
        AttributeTransformation transformationType;
        try {
            transformationType = AttributeTransformation.valueOf(normalizeTransformationValue(resolvedTransformation));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String resolvedFilePattern = resolveOptional(filePattern);

        return new ResolvedOptions(
            resolveOptional(source),
            resolveOptional(version),
            resolveOptional(moduleName),
            requireResolvedNonBlank(localName, "localName"),
            resolvedAttributePath,
            transformationType,
            resolvedFilePattern != null ? resolvedFilePattern : DEFAULT_FILE_PATTERN
        );
    }

    private static boolean isUnsupportedTransformationPath(String attributePath) {
        return !SUPPORTED_PATHS.contains(attributePath);
    }

    private static @Nullable String resolveOptional(@Nullable String value) {
        String normalizedValue = TopLevelBlockRecipeSupport.normalizeNullable(value);
        if (normalizedValue == null) {
            return null;
        }

        String resolved = PropertyPlaceholderResolver.resolve(normalizedValue);
        return TopLevelBlockRecipeSupport.normalizeNullable(resolved);
    }

    private static String requireResolvedNonBlank(String value, String fieldName) {
        return TopLevelBlockRecipeSupport.resolveRequiredValue(value, fieldName);
    }

    private static boolean matchesModuleFilters(Hcl.ConfigFile configFile,
                                                @Nullable String source,
                                                @Nullable String version,
                                                @Nullable String name) {
        if (source == null && version == null && name == null) {
            return true;
        }

        for (BodyContent bc : configFile.getBody()) {
            if (!(bc instanceof Hcl.Block)) {
                continue;
            }
            Hcl.Block block = (Hcl.Block) bc;
            if (!"module".equals(blockTypeName(block))) {
                continue;
            }
            if (matchesModuleBlock(block, source, version, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesModuleBlock(Hcl.Block block,
                                              @Nullable String source,
                                              @Nullable String version,
                                              @Nullable String name) {
        if (name != null && !ModuleBlockPredicates.matchesModuleName(block, name)) {
            return false;
        }

        if (source != null) {
            String actualSource = ModuleBlockPredicates.getAttributeValue(block, "source");
            if (!source.equals(actualSource)) {
                return false;
            }
        }

        if (version != null) {
            String actualVersion = ModuleBlockPredicates.getAttributeValue(block, "version");
            return version.equals(actualVersion);
        }

        return true;
    }

    private static String blockTypeName(Hcl.Block block) {
        Hcl.Identifier type = block.getType();
        if (type != null) {
            return type.getName().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();

        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "localName", localName);
        validated = TopLevelBlockRecipeSupport.validateRequiredHclIdentifier(validated, "localName", localName);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "attributePath", attributePath);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "transformation", transformation);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "source", source);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "version", version);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "moduleName", moduleName);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "filePattern", filePattern);

        String normalizedLocalName = TopLevelBlockRecipeSupport.normalizeNullable(localName);
        if (normalizedLocalName != null) {
            try {
                String resolvedLocalName = requireResolvedNonBlank(normalizedLocalName, "localName");
                if (normalizedLocalName.contains("${") && !resolvedLocalName.matches(HCL_IDENTIFIER_PATTERN)) {
                    validated = validated.and(Validated.invalid(
                        "localName",
                        localName,
                        "'localName' must be a valid HCL identifier matching [A-Za-z_][A-Za-z0-9_]*."
                    ));
                }
            } catch (IllegalStateException e) {
                validated = validated.and(Validated.invalid(
                    "localName",
                    localName,
                    e.getMessage()
                ));
            }
        }

        String normalizedTransformation = TopLevelBlockRecipeSupport.normalizeNullable(transformation);
        if (normalizedTransformation != null) {
            try {
                String resolvedTransformation = requireResolvedNonBlank(normalizedTransformation, "transformation");
                AttributeTransformation.valueOf(normalizeTransformationValue(resolvedTransformation));
            } catch (IllegalArgumentException e) {
                validated = validated.and(Validated.invalid(
                    "transformation",
                    transformation,
                    "Unknown transformation type. Supported: stringToList, listToString"
                ));
            } catch (IllegalStateException e) {
                validated = validated.and(Validated.invalid(
                    "transformation",
                    transformation,
                    e.getMessage()
                ));
            }
        }

        String normalizedAttributePath = TopLevelBlockRecipeSupport.normalizeNullable(attributePath);
        if (normalizedAttributePath != null) {
            try {
                String resolvedAttributePath = requireResolvedNonBlank(normalizedAttributePath, "attributePath");
                if (isUnsupportedTransformationPath(resolvedAttributePath)) {
                    validated = validated.and(Validated.invalid(
                        "attributePath",
                        attributePath,
                        "Unsupported attributePath. Supported values: " + String.join(", ", SUPPORTED_ATTRIBUTE_PATHS)
                    ));
                }
            } catch (IllegalStateException e) {
                validated = validated.and(Validated.invalid(
                    "attributePath",
                    attributePath,
                    e.getMessage()
                ));
            }
        }

        return validated;
    }

    private static String normalizeTransformationValue(String rawValue) {
        String withUnderscores = CAMEL_CASE_BOUNDARY.matcher(rawValue).replaceAll("_$1");
        return withUnderscores
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
    }

    enum AttributeTransformation {
        STRING_TO_LIST,
        LIST_TO_STRING
    }
}


