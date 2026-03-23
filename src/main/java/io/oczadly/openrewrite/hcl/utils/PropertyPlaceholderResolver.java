package io.oczadly.openrewrite.hcl.utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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

        ResolutionResult result = resolvePlaceholders(value, effectiveProperties, true);
        if (!result.unresolvedKeys.isEmpty()) {
            throw new IllegalStateException(
                "Failed to resolve property placeholders in: '" + value + "' (unresolved keys: " + String.join(", ", result.unresolvedKeys) + ")"
            );
        }
        return result.resolved;
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

    private static final class ResolutionResult {
        private final String resolved;
        private final List<String> unresolvedKeys;

        private ResolutionResult(String resolved, List<String> unresolvedKeys) {
            this.resolved = resolved;
            this.unresolvedKeys = unresolvedKeys;
        }
    }
}

