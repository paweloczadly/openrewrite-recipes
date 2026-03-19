package io.oczadly.openrewrite.hcl.utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for resolving placeholders in recipe configuration fields.
 * Supports ${property} and ${property:default} syntax.
 * Handles nested placeholders and Terraform expressions correctly.
 */
public final class PropertyPlaceholderResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

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

        // Extract placeholder keys from the original input
        Set<String> originalKeys = extractPlaceholderKeys(value);
        if (originalKeys.isEmpty()) {
            return value;
        }

        // Resolve placeholders
        ResolutionResult result = resolvePlaceholders(value, effectiveProperties, true);

        // Check only whether the original placeholders were resolved.
        // The resolved value may legitimately contain ${...} syntax (e.g. Terraform expressions)
        // that must be treated as literal text, not as further placeholders to resolve.
        Set<String> unresolvedFromInput = new LinkedHashSet<>();
        for (String key : originalKeys) {
            if (!isKeyResolved(result.resolved, key, effectiveProperties)) {
                unresolvedFromInput.add(key);
            }
        }

        if (!unresolvedFromInput.isEmpty()) {
            throw new IllegalStateException(
                "Failed to resolve property placeholders in: '" + value + "' (unresolved keys: " + String.join(", ", unresolvedFromInput) + ")"
            );
        }
        return result.resolved;
    }

    /**
     * Returns true if the given key was successfully resolved (either has a property value or a default).
     */
    private static boolean isKeyResolved(String resolved, String key, Properties properties) {
        // If the property exists, it was resolved
        if (properties.getProperty(key) != null) {
            return true;
        }
        // If the placeholder pattern still exists literally, it's unresolved
        return !resolved.contains("${" + key + "}");
    }

    private static Set<String> extractPlaceholderKeys(String value) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            String placeholderContent = matcher.group(1);
            // Extract key before ':' (default separator)
            int separatorIdx = placeholderContent.indexOf(':');
            String key = separatorIdx != -1 ? placeholderContent.substring(0, separatorIdx) : placeholderContent;
            keys.add(key);
        }
        return keys;
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
            String propertyValue = properties.getProperty(parts.key);

            if (propertyValue != null) {
                resolved.append(propertyValue);
            } else if (parts.defaultValue != null) {
                // Recursively resolve nested placeholders in default values,
                // but do NOT fail on unresolved keys (they might be Terraform expressions)
                ResolutionResult defaultResolution = resolvePlaceholders(parts.defaultValue, properties, false);
                resolved.append(defaultResolution.resolved);
                // Only propagate unresolved keys if we're in top-level resolution (failOnUnresolved=true)
                if (failOnUnresolved) {
                    unresolvedKeys.addAll(defaultResolution.unresolvedKeys);
                }
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

    private static ResolutionResult resolvePlaceholders(String input, Properties properties) {
        return resolvePlaceholders(input, properties, true);
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
            return new PlaceholderParts(placeholderBody, null);
        }
        String key = placeholderBody.substring(0, separator);
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

    private static final class PlaceholderParts {
        private final String key;
        private final @Nullable String defaultValue;

        private PlaceholderParts(String key, @Nullable String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
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

