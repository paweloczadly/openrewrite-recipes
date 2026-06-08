package io.oczadly.openrewrite.hcl.utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Terraform/OpenTofu-style semantic version constraint matcher.
 */
public final class VersionConstraintMatcher {

    public static final String INVALID_CONSTRAINT_MESSAGE = "'version' must be a valid semantic version constraint.";

    private VersionConstraintMatcher() {
    }

    public static boolean matches(String recipeConstraint, @Nullable String moduleVersionValue) {
        Version moduleVersion = Version.parseConcrete(moduleVersionValue);
        if (moduleVersion == null) {
            return false;
        }

        for (Constraint constraint : parseConstraints(recipeConstraint)) {
            if (!constraint.matches(moduleVersion)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidConstraint(@Nullable String recipeConstraint) {
        if (recipeConstraint == null || recipeConstraint.trim().isEmpty()) {
            return false;
        }
        try {
            parseConstraints(recipeConstraint);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static List<Constraint> parseConstraints(String expression) {
        String[] parts = expression.split(",", -1);
        List<Constraint> constraints = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Empty version constraint");
            }
            constraints.addAll(parseConstraint(trimmed));
        }
        return constraints;
    }

    private static List<Constraint> parseConstraint(String expression) {
        String operator;
        String version;
        if (expression.startsWith("~>")) {
            operator = "~>";
            version = expression.substring(2).trim();
        } else if (expression.startsWith(">=") || expression.startsWith("<=") || expression.startsWith("!=")) {
            operator = expression.substring(0, 2);
            version = expression.substring(2).trim();
        } else if (expression.startsWith(">") || expression.startsWith("<") || expression.startsWith("=")) {
            operator = expression.substring(0, 1);
            version = expression.substring(1).trim();
        } else {
            operator = "=";
            version = expression;
        }

        Version parsedVersion = Version.parseConstraintVersion(version);
        if (parsedVersion == null) {
            throw new IllegalArgumentException("Invalid version constraint");
        }

        List<Constraint> constraints = new ArrayList<>();
        switch (operator) {
            case "=":
                constraints.add(new Constraint(Operator.EQUAL, parsedVersion));
                break;
            case "!=":
                constraints.add(new Constraint(Operator.NOT_EQUAL, parsedVersion));
                break;
            case ">":
                constraints.add(new Constraint(Operator.GREATER_THAN, parsedVersion));
                break;
            case ">=":
                constraints.add(new Constraint(Operator.GREATER_THAN_OR_EQUAL, parsedVersion));
                break;
            case "<":
                constraints.add(new Constraint(Operator.LESS_THAN, parsedVersion));
                break;
            case "<=":
                constraints.add(new Constraint(Operator.LESS_THAN_OR_EQUAL, parsedVersion));
                break;
            case "~>":
                constraints.add(new Constraint(Operator.GREATER_THAN_OR_EQUAL, parsedVersion));
                constraints.add(new Constraint(Operator.LESS_THAN, parsedVersion.nextPessimisticUpperBound()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported version constraint operator");
        }
        return constraints;
    }

    private enum Operator {
        EQUAL,
        NOT_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL
    }

    private static final class Constraint {
        private final Operator operator;
        private final Version version;

        private Constraint(Operator operator, Version version) {
            this.operator = operator;
            this.version = version;
        }

        private boolean matches(Version candidate) {
            int comparison = candidate.compareTo(version);
            switch (operator) {
                case EQUAL:
                    return comparison == 0;
                case NOT_EQUAL:
                    return comparison != 0;
                case GREATER_THAN:
                    return comparison > 0;
                case GREATER_THAN_OR_EQUAL:
                    return comparison >= 0;
                case LESS_THAN:
                    return comparison < 0;
                case LESS_THAN_OR_EQUAL:
                    return comparison <= 0;
                default:
                    throw new IllegalStateException("Unhandled version constraint operator: " + operator);
            }
        }
    }

    private static final class Version implements Comparable<Version> {
        private final int major;
        private final int minor;
        private final int patch;
        private final int specifiedSegments;

        private Version(int major, int minor, int patch, int specifiedSegments) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.specifiedSegments = specifiedSegments;
        }

        private static @Nullable Version parseConcrete(@Nullable String value) {
            Version version = parseVersion(value);
            return version != null && version.specifiedSegments == 3 ? version : null;
        }

        private static @Nullable Version parseConstraintVersion(@Nullable String value) {
            return parseVersion(value);
        }

        private static @Nullable Version parseVersion(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return null;
            }

            String[] segments = trimmed.split("\\.", -1);
            if (segments.length < 1 || segments.length > 3) {
                return null;
            }

            int[] parsed = new int[]{0, 0, 0};
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                if (segment.isEmpty() || !segment.matches("0|[1-9][0-9]*")) {
                    return null;
                }
                try {
                    parsed[i] = Integer.parseInt(segment);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return new Version(parsed[0], parsed[1], parsed[2], segments.length);
        }

        private Version nextPessimisticUpperBound() {
            if (specifiedSegments == 3) {
                return new Version(major, minor + 1, 0, 3);
            }
            return new Version(major + 1, 0, 0, 3);
        }

        @Override
        public int compareTo(Version other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) {
                return majorComparison;
            }
            int minorComparison = Integer.compare(minor, other.minor);
            if (minorComparison != 0) {
                return minorComparison;
            }
            return Integer.compare(patch, other.patch);
        }
    }
}
