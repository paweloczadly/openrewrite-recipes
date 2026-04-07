package io.oczadly.openrewrite.hcl;

import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.Label;
import org.openrewrite.SourceFile;
import org.openrewrite.tree.ParseError;

import java.util.ArrayList;
import java.util.List;

final class ProviderRecipeSupport {

    private static final String DEFAULT_FILE_PATTERN = "**/*.tf";

    private ProviderRecipeSupport() {
    }

    static TreeVisitor<?, ExecutionContext> scopedVisitor(@Nullable String filePattern,
                                                          HclVisitor<ExecutionContext> visitor) {
        return Preconditions.check(new FindSourceFiles(filePattern != null ? filePattern : DEFAULT_FILE_PATTERN), visitor);
    }

    static boolean matchesModuleFilters(Hcl.ConfigFile configFile,
                                        @Nullable String moduleName,
                                        @Nullable String moduleSource,
                                        @Nullable String moduleVersion) {
        if (moduleName == null && moduleSource == null && moduleVersion == null) {
            return true;
        }

        for (BodyContent bodyContent : configFile.getBody()) {
            if (!(bodyContent instanceof Hcl.Block)) {
                continue;
            }

            Hcl.Block block = (Hcl.Block) bodyContent;
            if (!isBlockType(block, "module")) {
                continue;
            }
            if (!ModuleBlockPredicates.matchesModuleName(block, moduleName)) {
                continue;
            }
            if (moduleSource != null && !moduleSource.equals(ModuleBlockPredicates.getAttributeValue(block, "source"))) {
                continue;
            }
            if (moduleVersion != null && !moduleVersion.equals(ModuleBlockPredicates.getAttributeValue(block, "version"))) {
                continue;
            }

            return true;
        }

        return false;
    }

    static boolean isBlockType(Hcl.Block block, String blockType) {
        Hcl.Identifier type = block.getType();
        return type != null && blockType.equalsIgnoreCase(type.getName());
    }

    static Hcl.Block findTopLevelBlock(Hcl.ConfigFile configFile, String blockType) {
        for (BodyContent bodyContent : configFile.getBody()) {
            if (!(bodyContent instanceof Hcl.Block)) {
                continue;
            }

            Hcl.Block block = (Hcl.Block) bodyContent;
            if (isBlockType(block, blockType)) {
                return block;
            }
        }

        return null;
    }

    static Hcl.Block findNestedBlock(Hcl.Block parent, String blockType) {
        for (BodyContent bodyContent : parent.getBody()) {
            if (!(bodyContent instanceof Hcl.Block)) {
                continue;
            }

            Hcl.Block block = (Hcl.Block) bodyContent;
            if (isBlockType(block, blockType)) {
                return block;
            }
        }

        return null;
    }

    static Hcl.ConfigFile replaceTopLevelBlock(Hcl.ConfigFile configFile,
                                               Hcl.Block existingBlock,
                                               Hcl.Block replacementBlock) {
        List<BodyContent> updatedBody = new ArrayList<>(configFile.getBody().size());
        for (BodyContent bodyContent : configFile.getBody()) {
            if (bodyContent == existingBlock) {
                updatedBody.add(replacementBlock);
            } else {
                updatedBody.add(bodyContent);
            }
        }
        return configFile.withBody(updatedBody);
    }

    static Hcl.Block replaceNestedBlock(Hcl.Block parent,
                                        Hcl.Block existingBlock,
                                        Hcl.Block replacementBlock) {
        List<BodyContent> updatedBody = new ArrayList<>(parent.getBody().size());
        for (BodyContent bodyContent : parent.getBody()) {
            if (bodyContent == existingBlock) {
                updatedBody.add(replacementBlock);
            } else {
                updatedBody.add(bodyContent);
            }
        }
        return parent.withBody(updatedBody);
    }

    static String childIndent(Hcl.Block parent, String fallback) {
        for (BodyContent bodyContent : parent.getBody()) {
            String prefix = bodyContent.getPrefix().getWhitespace();
            int newlineIndex = prefix.lastIndexOf('\n');
            if (newlineIndex >= 0) {
                return prefix.substring(newlineIndex + 1);
            }
        }
        return fallback;
    }

    static boolean matchesProviderNameLabel(Hcl.Block block, String providerName) {
        if (block.getLabels().isEmpty()) {
            return false;
        }

        Label label = block.getLabels().get(0);
        if (label instanceof Hcl.Literal) {
            String valueSource = ((Hcl.Literal) label).getValueSource();
            return providerName.equals(ModuleBlockPredicates.removeQuotes(valueSource));
        }

        if (label instanceof Hcl.QuotedTemplate) {
            Hcl.QuotedTemplate quoted = (Hcl.QuotedTemplate) label;
            if (quoted.getExpressions().size() != 1) {
                return false;
            }
            Hcl quotedExpression = quoted.getExpressions().get(0);
            if (quotedExpression instanceof Hcl.Literal) {
                String valueSource = ((Hcl.Literal) quotedExpression).getValueSource();
                return providerName.equals(ModuleBlockPredicates.removeQuotes(valueSource));
            }
        }

        return false;
    }

    static Hcl.Block parseSingleBlock(HclParser parser, String hclText, String purpose) {
        SourceFile sourceFile = parser.parse(hclText)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not parse generated HCL for " + purpose));

        if (sourceFile instanceof ParseError) {
            throw new IllegalStateException("Could not parse generated HCL for " + purpose + ": " + sourceFile);
        }
        if (!(sourceFile instanceof Hcl.ConfigFile)) {
            throw new IllegalStateException("Expected parsed HCL for " + purpose + " to be ConfigFile but got " + sourceFile.getClass().getSimpleName());
        }

        Hcl.ConfigFile parsed = (Hcl.ConfigFile) sourceFile;
        if (parsed.getBody().isEmpty() || !(parsed.getBody().get(0) instanceof Hcl.Block)) {
            throw new IllegalStateException("Generated HCL for " + purpose + " does not contain a top-level block");
        }

        return (Hcl.Block) parsed.getBody().get(0);
    }


    static String indentBlockBody(String body, String indent) {
        String[] lines = body.split("\\R", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(indent).append(lines[i]);
        }
        return result.toString();
    }

    static String directoryOf(String path) {
        java.nio.file.Path p = java.nio.file.Paths.get(path);
        java.nio.file.Path parent = p.getParent();
        return parent != null ? parent.toString() : "";
    }

    static boolean matchesPattern(String path, String pattern) {
        java.nio.file.PathMatcher matcher = java.nio.file.FileSystems.getDefault()
            .getPathMatcher("glob:" + pattern);
        java.nio.file.Path candidate = java.nio.file.Paths.get(path);
        if (matcher.matches(candidate)) {
            return true;
        }
        if (candidate.getParent() == null && pattern.startsWith("**/")) {
            java.nio.file.PathMatcher rootMatcher = java.nio.file.FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern.substring(3));
            return rootMatcher.matches(candidate);
        }
        return false;
    }
}
