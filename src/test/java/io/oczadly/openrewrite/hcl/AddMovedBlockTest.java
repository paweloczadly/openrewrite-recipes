package io.oczadly.openrewrite.hcl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Validated;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.oczadly.openrewrite.hcl.utils.SystemPropertyTestSupport.restoreSystemProperty;
import static org.openrewrite.hcl.Assertions.hcl;

public class AddMovedBlockTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddMovedBlock(
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet",
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldAddMovedBlockWithLiteralValues() {
        rewriteRun(
            hcl(
                """
                module "vnet" {
                  source  = "Azure/avm-res-network-virtualnetwork/azapi"
                  version = "0.14.1"
                }
                """,
                """
                module "vnet" {
                  source  = "Azure/avm-res-network-virtualnetwork/azapi"
                  version = "0.14.1"
                }

                moved {
                  from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                  to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                }
                """
            )
        );
    }

    @Test
    void shouldAllowCommentTokensInsideQuotedTraversalKeys() {
        rewriteRun(
            spec -> spec.recipe(new AddMovedBlock(
                "module.vnet.module.subnet[\"a//b\"].azapi_resource.subnet",
                "module.vnet.module.subnet[\"a/*b\"].azapi_resource.subnet_ipam[0]",
                null
            )),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """,
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }

                moved {
                  from = module.vnet.module.subnet["a//b"].azapi_resource.subnet
                  to   = module.vnet.module.subnet["a/*b"].azapi_resource.subnet_ipam[0]
                }
                """
            )
        );
    }

    @Test
    void shouldRejectCommentSyntaxOutsideQuotedTraversal() {
        AddMovedBlock recipe = new AddMovedBlock(
            "module.vnet//broken.module.subnet[\"subnet01\"].azapi_resource.subnet",
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("'from' must be a single-line HCL traversal reference but contains comment syntax: module.vnet//broken.module.subnet[\"subnet01\"].azapi_resource.subnet");
    }

    @Test
    void shouldAddMovedBlockWithPlaceholderInterpolation() {
        String moduleNameKey = "avm.moved.moduleName.ref";
        String subnetNameKey = "avm.moved.subnetName.ref";
        String moduleNamePreviousValue = System.getProperty(moduleNameKey);
        String subnetNamePreviousValue = System.getProperty(subnetNameKey);

        System.setProperty(moduleNameKey, "vnet");
        System.setProperty(subnetNameKey, "subnet01");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddMovedBlock(
                    "module.${avm.moved.moduleName.ref}.module.subnet[\"${avm.moved.subnetName.ref}\"].azapi_resource.subnet",
                    "module.${avm.moved.moduleName.ref}.module.subnet[\"${avm.moved.subnetName.ref}\"].azapi_resource.subnet_ipam[0]",
                    null
                )),
                hcl(
                    """
                    module "vnet" {
                      source = "Azure/avm-res-network-virtualnetwork/azapi"
                    }
                    """,
                    """
                    module "vnet" {
                      source = "Azure/avm-res-network-virtualnetwork/azapi"
                    }

                    moved {
                      from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                      to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(moduleNameKey, moduleNamePreviousValue);
            restoreSystemProperty(subnetNameKey, subnetNamePreviousValue);
        }
    }

    @Test
    void shouldPreserveLiteralTerraformInterpolationInFromAndTo() {
        rewriteRun(
            spec -> spec.recipe(new AddMovedBlock(
                "module.vnet.module.subnet[\"${{local.subnet_name}}\"].azapi_resource.subnet",
                "module.vnet.module.subnet[\"${{local.subnet_name}}\"].azapi_resource.subnet_ipam[0]",
                null
            )),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """,
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }

                moved {
                  from = module.vnet.module.subnet["${local.subnet_name}"].azapi_resource.subnet
                  to   = module.vnet.module.subnet["${local.subnet_name}"].azapi_resource.subnet_ipam[0]
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesMatchingPattern() {
        rewriteRun(
            spec -> spec.recipe(new AddMovedBlock(
                "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet",
                "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
                "**/prod/**/*.tf"
            )),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """,
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }

                moved {
                  from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                  to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                }
                """,
                sourceSpecs -> sourceSpecs.path("env/prod/main.tf")
            ),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """,
                sourceSpecs -> sourceSpecs.path("env/dev/main.tf")
            )
        );
    }

    @Test
    void shouldThrowExceptionWhenPlaceholderIsNotSet() {
        String missingKey = "avm.moved.from.missing";

        AddMovedBlock recipe = new AddMovedBlock(
            "${" + missingKey + "}",
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Failed to resolve property placeholders in: '${" + missingKey + "}' (unresolved keys: " + missingKey + ")");
    }

    @Test
    void shouldNotAddDuplicateMovedBlockWhenAlreadyExists() {
        rewriteRun(
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }

                moved {
                  from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                  to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyAddMovedBlockWhenMatchingModuleExists() {
        rewriteRun(
            spec -> spec.recipe(new AddMovedBlock(
                "vnet",
                "Azure/avm-res-network-virtualnetwork/azapi",
                "0.14.1",
                "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet",
                "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
                null
            )),
            hcl(
                """
                module "vnet" {
                  source  = "Azure/avm-res-network-virtualnetwork/azapi"
                  version = "0.14.1"
                }
                """,
                """
                module "vnet" {
                  source  = "Azure/avm-res-network-virtualnetwork/azapi"
                  version = "0.14.1"
                }

                moved {
                  from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                  to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                }
                """
            ),
            hcl(
                """
                module "vnet" {
                  source  = "Azure/avm-res-network-virtualnetwork/azapi"
                  version = "0.15.0"
                }
                """
            )
        );
    }

    @Test
    void shouldResolveModuleFilterPlaceholders() {
        String moduleNameKey = "avm.moved.filter.moduleName";
        String sourceKey = "avm.moved.filter.source";
        String versionKey = "avm.moved.filter.version";
        String moduleNamePrevious = System.getProperty(moduleNameKey);
        String sourcePrevious = System.getProperty(sourceKey);
        String versionPrevious = System.getProperty(versionKey);

        System.setProperty(moduleNameKey, "vnet");
        System.setProperty(sourceKey, "Azure/avm-res-network-virtualnetwork/azapi");
        System.setProperty(versionKey, "0.14.1");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddMovedBlock(
                    "${avm.moved.filter.moduleName}",
                    "${avm.moved.filter.source}",
                    "${avm.moved.filter.version}",
                    "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet",
                    "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
                    null
                )),
                hcl(
                    """
                    module "vnet" {
                      source  = "Azure/avm-res-network-virtualnetwork/azapi"
                      version = "0.14.1"
                    }
                    """,
                    """
                    module "vnet" {
                      source  = "Azure/avm-res-network-virtualnetwork/azapi"
                      version = "0.14.1"
                    }

                    moved {
                      from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                      to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(moduleNameKey, moduleNamePrevious);
            restoreSystemProperty(sourceKey, sourcePrevious);
            restoreSystemProperty(versionKey, versionPrevious);
        }
    }

    @Test
    void shouldNotAddDuplicateMovedBlockWhenAttributesHaveDifferentOrder() {
        rewriteRun(
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }

                moved {
                  to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
                  from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
                }
                """
            )
        );
    }

    @Test
    void shouldThrowExceptionWhenPlaceholderResolvesToBlank() {
        String fromProperty = "avm.moved.from.blank";
        String fromPreviousValue = System.getProperty(fromProperty);

        System.setProperty(fromProperty, "   ");

        try {
            AddMovedBlock recipe = new AddMovedBlock(
                "${" + fromProperty + "}",
                "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
                null
            );

            assertThatThrownBy(() -> rewriteRun(
                spec -> spec.recipe(recipe),
                hcl(
                    """
                    module "vnet" {
                      source = "Azure/avm-res-network-virtualnetwork/azapi"
                    }
                    """
                )
            ))
                .satisfies(throwable -> {
                    Throwable root = throwable;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root).isInstanceOf(IllegalStateException.class);
                    assertThat(root.getMessage()).isEqualTo("Placeholder '${" + fromProperty + "}' for 'from' resolved to an empty or blank value");
                });
        } finally {
            restoreSystemProperty(fromProperty, fromPreviousValue);
        }
    }

    @ParameterizedTest(name = "should reject invalid from=''{0}'' to=''{1}''")
    @CsvSource(delimiter = '|', textBlock = """
        ''    | value | 'from' must be specified and cannot be empty.
        value | ''    | 'to' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidOptions(String from, String to, String expectedMessage) {
        AddMovedBlock recipe = new AddMovedBlock(from, to, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest(name = "should reject blank module filters moduleName=''{0}'' source=''{1}'' version=''{2}''")
    @CsvSource(delimiter = '|', textBlock = """
        ' ' | value | value | 'moduleName' cannot be empty when specified.
        value | ' ' | value | 'source' cannot be empty when specified.
        value | value | ' ' | 'version' cannot be empty when specified.
        """)
    void shouldRejectBlankOptionalModuleFilters(String moduleName, String source, String version, String expectedMessage) {
        AddMovedBlock recipe = new AddMovedBlock(
            moduleName,
            source,
            version,
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet",
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void shouldThrowExceptionWhenFromIsQuotedStringLiteral() {
        AddMovedBlock recipe = new AddMovedBlock(
            "\"module.vnet\"",
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("'from' must be an HCL traversal reference (e.g. module.foo) but looks like a quoted string literal: \"module.vnet\"");
    }

    @Test
    void shouldThrowExceptionWhenFromDoesNotStartWithIdentifier() {
        AddMovedBlock recipe = new AddMovedBlock(
            "0module.vnet",
            "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]",
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "vnet" {
                  source = "Azure/avm-res-network-virtualnetwork/azapi"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("'from' must be an HCL traversal reference starting with a letter or underscore, but got: 0module.vnet");
    }
}

