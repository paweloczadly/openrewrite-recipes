package io.oczadly.openrewrite.hcl.utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Utility for resolving placeholders in recipe configuration fields.
 * Supports ${property} and ${property:default} syntax.
 * Resolves nested placeholders, including nested defaults.
 * Unresolved placeholders inside default values are preserved as literals,
 * which allows Terraform expressions like ${data.remote_state.output} in defaults.
 */
public final class PropertyPlaceholderResolver {

    private PropertyPlaceholderResolver() {
    }

    public static @Nullable String resolve(@Nullable String value) {
        return resolve(value, null);
    }

    public static @Nullable String resolve(@Nullable String value, @Nullable Properties properties) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        Properties effectiveProperties = properties != null ? properties : System.getProperties();

        if (!value.contains("${{")) {
            ResolutionResult result = resolvePlaceholders(value, effectiveProperties, true);
            if (!result.unresolvedKeys.isEmpty()) {
                throw new IllegalStateException(
                    "Failed to resolve property placeholders in: '" + value + "' (unresolved keys: " + String.join(", ", result.unresolvedKeys) + ")"
                );
            }
            return result.resolved;
        }

        LiteralEscapeResult escapedValue = escapeLiteralTerraformPlaceholders(value);

        ResolutionResult result = resolvePlaceholders(escapedValue.escapedInput, effectiveProperties, true);
        if (!result.unresolvedKeys.isEmpty()) {
            throw new IllegalStateException(
                "Failed to resolve property placeholders in: '" + value + "' (unresolved keys: " + String.join(", ", result.unresolvedKeys) + ")"
            );
        }
        return restoreLiteralTerraformPlaceholders(result.resolved, escapedValue.literals);
    }

    private static LiteralEscapeResult escapeLiteralTerraformPlaceholders(String input) {
        StringBuilder escaped = new StringBuilder(input.length());
        List<LiteralPlaceholder> literals = new ArrayList<>();
        String tokenPrefix = createUniqueLiteralTokenPrefix(input);
        int i = 0;

        while (i < input.length()) {
            if (input.startsWith("${{", i)) {
                int end = findLiteralTerraformPlaceholderEnd(input, i);
                if (end < 0) {
                    throw new IllegalStateException("Failed to resolve property placeholders in: '" + input + "'");
                }

                String literalBody = input.substring(i + 3, end);
                String token = tokenPrefix + literals.size() + "__";
                literals.add(new LiteralPlaceholder(token, "${" + literalBody + "}"));
                escaped.append(token);
                i = end + 2;
                continue;
            }

            escaped.append(input.charAt(i));
            i++;
        }

        return new LiteralEscapeResult(escaped.toString(), literals);
    }

    private static int findLiteralTerraformPlaceholderEnd(String input, int start) {
        int nesting = 1;
        for (int i = start + 3; i < input.length() - 1; i++) {
            if (input.startsWith("${{", i)) {
                nesting++;
                i += 2;
                continue;
            }
            if (input.startsWith("}}", i)) {
                nesting--;
                if (nesting == 0) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    private static String restoreLiteralTerraformPlaceholders(String resolved, List<LiteralPlaceholder> literals) {
        String restored = resolved;
        for (LiteralPlaceholder literal : literals) {
            restored = restored.replace(literal.token, literal.literalValue);
        }
        return restored;
    }

    private static String createUniqueLiteralTokenPrefix(String input) {
        String tokenPrefix;
        do {
            tokenPrefix = "__ORHCL_LITERAL_" + UUID.randomUUID().toString().replace('-', '_') + "_";
        } while (input.contains(tokenPrefix));
        return tokenPrefix;
    }

    private static ResolutionResult resolvePlaceholders(String input, Properties properties, boolean failOnUnresolved) {
        StringBuilder resolved = new StringBuilder(input.length());
        Set<String> unresolvedKeys = new LinkedHashSet<>();

        int cursor = 0;
        while (cursor < input.length()) {
            int placeholderStart = input.indexOf("${", cursor);
            if (placeholderStart < 0) {
                resolved.append(input.substring(cursor));
                break;
            }

            resolved.append(input, cursor, placeholderStart);

            int placeholderEnd = findPlaceholderEnd(input, placeholderStart);
            if (placeholderEnd < 0) {
                throw new IllegalStateException("Failed to resolve property placeholders in: '" + input + "'");
            }

            String placeholderBody = input.substring(placeholderStart + 2, placeholderEnd);
            PlaceholderParts parts = splitPlaceholderParts(placeholderBody);
            if (!isValidKey(parts.key)) {
                throw new IllegalStateException("Failed to resolve property placeholders in: '" + input + "'");
            }
            String propertyValue = properties.getProperty(parts.key);

            if (propertyValue != null) {
                resolved.append(propertyValue);
            } else if (parts.defaultValue != null) {
                // Resolve placeholders in defaults opportunistically; unresolved ${...}
                // literals are preserved for Terraform-style expressions.
                ResolutionResult defaultResolution = resolvePlaceholders(parts.defaultValue, properties, false);
                resolved.append(defaultResolution.resolved);
            } else {
                if (failOnUnresolved) {
                    unresolvedKeys.add(parts.key);
                }
                resolved.append(input, placeholderStart, placeholderEnd + 1);
            }

            cursor = placeholderEnd + 1;
        }

        return new ResolutionResult(resolved.toString(), new ArrayList<>(unresolvedKeys));
    }

    private static int findPlaceholderEnd(String input, int start) {
        int nesting = 0;
        for (int i = start + 2; i < input.length(); i++) {
            if (input.startsWith("${", i)) {
                nesting++;
                i++;
                continue;
            }
            if (input.charAt(i) == '}') {
                if (nesting == 0) {
                    return i;
                }
                nesting--;
            }
        }
        return -1;
    }

    private static PlaceholderParts splitPlaceholderParts(String placeholderBody) {
        int separator = findTopLevelSeparator(placeholderBody);
        if (separator < 0) {
            return new PlaceholderParts(placeholderBody.trim(), null);
        }
        String key = placeholderBody.substring(0, separator).trim();
        String defaultValue = placeholderBody.substring(separator + 1);
        return new PlaceholderParts(key, defaultValue);
    }

    private static int findTopLevelSeparator(String placeholderBody) {
        int nesting = 0;
        for (int i = 0; i < placeholderBody.length(); i++) {
            if (placeholderBody.startsWith("${", i)) {
                nesting++;
                i++;
                continue;
            }
            char current = placeholderBody.charAt(i);
            if (current == '}') {
                if (nesting > 0) {
                    nesting--;
                }
                continue;
            }
            if (current == ':' && nesting == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isValidKey(String key) {
        return !key.trim().isEmpty() && !key.contains("${") && !key.contains("}");
    }

    private static final class PlaceholderParts {
        private final String key;
        private final @Nullable String defaultValue;

        private PlaceholderParts(String key, @Nullable String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    private static final class LiteralPlaceholder {
        private final String token;
        private final String literalValue;

        private LiteralPlaceholder(String token, String literalValue) {
            this.token = token;
            this.literalValue = literalValue;
        }
    }

    private static final class LiteralEscapeResult {
        private final String escapedInput;
        private final List<LiteralPlaceholder> literals;

        private LiteralEscapeResult(String escapedInput, List<LiteralPlaceholder> literals) {
            this.escapedInput = escapedInput;
            this.literals = literals;
        }
    }

    private static final class ResolutionResult {
        private final String resolved;
        private final List<String> unresolvedKeys;

        private ResolutionResult(String resolved, List<String> unresolvedKeys) {
            this.resolved = resolved;
            this.unresolvedKeys = unresolvedKeys;
        }
    }
}

