package io.oczadly.openrewrite.hcl;

import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Validated;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleRecipeTest implements RewriteTest {

    @ParameterizedTest
    @CsvSource({
        "module-name, source-value, 1.0.0, true",
        "module-name, source-value, , true",
        ", source-value, 1.0.0, true",
        ", source-value, , true",
        "module-name, , , false",
        ", , , false",
        "module-name, '', , false",
        ", '', , false"
    })
    void shouldValidateRecipeParameters(String moduleName, String source, String version, boolean shouldBeValid) {
        TestModuleRecipe recipe = new TestModuleRecipe(moduleName, source, version, null);
        Validated<Object> validation = recipe.validate();

        if (shouldBeValid) {
            assertThat(validation.isValid())
                .as("Recipe with moduleName='%s', source='%s', version='%s' should be valid", moduleName, source, version)
                .isTrue();
        } else {
            assertThat(validation.isInvalid())
                .as("Recipe with moduleName='%s', source='%s', version='%s' should be invalid", moduleName, source, version)
                .isTrue();
        }
    }

    @Test
    void shouldRejectNullSource() {
        TestModuleRecipe recipe = new TestModuleRecipe("module-name", null, null, null);
        Validated<Object> validation = recipe.validate();

        assertThat(validation.isInvalid()).isTrue();
        assertThat(validation.failures().getFirst().getProperty()).isEqualTo("source");
    }

    @VisibleForTesting
    public class TestModuleRecipe extends ModuleRecipe {
        protected TestModuleRecipe(String moduleName, String source, String version, String filePattern) {
            super(moduleName, source, version, filePattern);
        }

        @Override
        public @NonNull String getDisplayName() {
            return "Test module recipe";
        }

        @Override
        public @NonNull String getDescription() {
            return "Test recipe for validating ModuleRecipe behavior";
        }

        @Override
        protected HclVisitor<ExecutionContext> createModuleVisitor() {
            return new HclVisitor<>();
        }
    }
}
