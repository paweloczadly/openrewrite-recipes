package io.oczadly.openrewrite.hcl.utils;

import org.jspecify.annotations.Nullable;
import org.openrewrite.internal.PropertyPlaceholderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility for resolving placeholders in recipe configuration fields.
 * Supports ${property} and ${property:default} syntax.
 */
public final class PropertyPlaceholderResolver {

    private static final PropertyPlaceholderHelper HELPER =
        new PropertyPlaceholderHelper("${", "}", ":");

    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN =
        Pattern.compile("\\$\\{([^}]+)}");

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

        List<String> inputKeys = extractPlaceholderKeys(value);
        if (inputKeys.isEmpty()) {
            return value;
        }

        try {
            String resolved = HELPER.replacePlaceholders(value, effectiveProperties);
            // Only check whether the placeholders present in the original input were resolved.
            // The resolved value may legitimately contain ${...} syntax (e.g. Terraform expressions)
            // that must be treated as literal text, not as further placeholders to resolve.
            List<String> unresolvedKeys = inputKeys.stream()
                .filter(key -> HELPER.hasPlaceholders(resolved) && isKeyStillUnresolved(resolved, key, effectiveProperties))
                .collect(Collectors.toList());
            if (!unresolvedKeys.isEmpty()) {
                throw new IllegalStateException(
                    "Failed to resolve property placeholders in: '" + value + "' (unresolved keys: " + String.join(", ", unresolvedKeys) + ")"
                );
            }
            return resolved;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve property placeholders in: '" + value + "'", e);
        }
    }

    /**
     * Returns true if the given key is still present as an unresolved placeholder in the resolved string.
     * A key is considered unresolved when its placeholder pattern remains literally in the output,
     * i.e. the resolver could not find a value for it.
     */
    private static boolean isKeyStillUnresolved(String resolved, String key, Properties properties) {
        // If the property exists, it was resolved (even if the resolved value itself contains ${...})
        if (properties.getProperty(key) != null) {
            return false;
        }
        // No property and no default → placeholder remains literally in the output
        return resolved.contains("${" + key + "}");
    }

    private static List<String> extractPlaceholderKeys(String value) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = UNRESOLVED_PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            String key = matcher.group(1);
            // Strip default value (after ':') to show just the key name
            int separatorIdx = key.indexOf(':');
            keys.add(separatorIdx != -1 ? key.substring(0, separatorIdx) : key);
        }
        return keys;
    }
}

