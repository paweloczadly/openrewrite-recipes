package io.oczadly.openrewrite.hcl.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VersionConstraintMatcherTest {

    @ParameterizedTest(name = "constraint ''{0}'' should match module version ''{1}'' = {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        0.10.1                  | 0.10.1 | true
        = 0.10.1                | 0.10.1 | true
        != 0.10.1               | 0.10.2 | true
        > 0.10.1                | 0.10.2 | true
        >= 0.10.1               | 0.10.1 | true
        < 0.11.0                | 0.10.9 | true
        <= 0.10.1               | 0.10.1 | true
        ~> 0.10.1               | 0.10.1 | true
        ~> 0.10.1               | 0.10.2 | true
        ~> 0.10.1               | 0.11.0 | false
        ~> 0.10                 | 0.10.7 | true
        ~> 0.10                 | 1.0.0  | false
        >= 0.10.0, < 0.11.0     | 0.10.7 | true
        >= 0.10.0, < 0.11.0     | 0.11.0 | false
        >= 0.10.0               | 0.10   | false
        >= 0.10.0               | 0.10.x | false
        >= 0.10.0               |         | false
        >= 0.10.0               | ${var.module_version} | false
        ~> 0.3.5                | ~> 0.3.5 | true
        ~> 0.3                  | ~> 0.3.5 | true
        ~> 0.3.5                | ~> 0.3   | false
        >= 0.3.0                | ~> 0.3.5 | true
        ~> 0.4.0                | ~> 0.3.5 | false
        = 0.3.5                 | ~> 0.3.5 | true
        != 0.3.5                | ~> 0.3.5 | false
        >= 0.3.5                | >= 0.3.5 | true
        ~> 0.10.1               | = 0.10.1 | true
        ~> 0.10.1               | <= 0.10.1 | true
        = 0.3.5                 | != 0.3.5 | false
        >= 0.3.5                | != 0.3.5 | false
        """)
    void shouldMatchSupportedConstraints(String constraint, String moduleVersion, boolean expected) {
        assertThat(VersionConstraintMatcher.matches(constraint, moduleVersion)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "constraint ''{0}'' validity should be {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        0.10.1              | true
        >= 0.10.0, < 0.11.0 | true
        ~> 0.10.1           | true
        ""                  | false
        " "                 | false
        => 0.10.0           | false
        >=                  | false
        >= 0.10.x           | false
        >= 0.10.0,          | false
        0.10.0-beta         | false
        """)
    void shouldValidateConstraints(String constraint, boolean expected) {
        assertThat(VersionConstraintMatcher.isValidConstraint(constraint)).isEqualTo(expected);
    }
}
