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

public class AddRemovedBlockTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddRemovedBlock(
            "module.private_dns_zone.azurerm_private_dns_zone.this",
            false,
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldAddRemovedBlockWithLiteralFrom() {
        rewriteRun(
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }
                """,
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }

                removed {
                  from = module.private_dns_zone.azurerm_private_dns_zone.this
                  lifecycle {
                    destroy = false
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldAllowCommentTokensInsideQuotedTraversalKey() {
        rewriteRun(
            spec -> spec.recipe(new AddRemovedBlock(
                "module.private_dns_zone.module.virtual_network_links[\"a#b\"].azurerm_private_dns_zone_virtual_network_link.this",
                false,
                null
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """,
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                removed {
                  from = module.private_dns_zone.module.virtual_network_links["a#b"].azurerm_private_dns_zone_virtual_network_link.this
                  lifecycle {
                    destroy = false
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldRejectCommentSyntaxOutsideQuotedTraversal() {
        AddRemovedBlock recipe = new AddRemovedBlock(
            "module.private_dns_zone/*broken*/.azurerm_private_dns_zone.this",
            false,
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("'from' must be a single-line HCL traversal reference but contains comment syntax: module.private_dns_zone/*broken*/.azurerm_private_dns_zone.this");
    }

    @Test
    void shouldAddRemovedBlockWithPlaceholderInterpolation() {
        String moduleNameKey = "avm.removed.moduleName.ref";
        String resourceKey = "avm.removed.resourceName.ref";

        String moduleNamePreviousValue = System.getProperty(moduleNameKey);
        String resourcePreviousValue = System.getProperty(resourceKey);
        System.setProperty(moduleNameKey, "private_dns_zone");
        System.setProperty(resourceKey, "azurerm_private_dns_zone_virtual_network_link.this");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddRemovedBlock(
                    "module.${avm.removed.moduleName.ref}.${avm.removed.resourceName.ref}",
                    true,
                    null
                )),
                hcl(
                    """
                    module "private_dns_zone" {
                      source = "Azure/avm-res-network-privatednszone/azurerm"
                    }
                    """,
                    """
                    module "private_dns_zone" {
                      source = "Azure/avm-res-network-privatednszone/azurerm"
                    }

                    removed {
                      from = module.private_dns_zone.azurerm_private_dns_zone_virtual_network_link.this
                      lifecycle {
                        destroy = true
                      }
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(moduleNameKey, moduleNamePreviousValue);
            restoreSystemProperty(resourceKey, resourcePreviousValue);
        }
    }

    @Test
    void shouldPreserveLiteralTerraformInterpolationInFrom() {
        rewriteRun(
            spec -> spec.recipe(new AddRemovedBlock(
                "module.vnet.module.subnet[\"${{local.subnet_name}}\"].azapi_resource.subnet",
                false,
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

                removed {
                  from = module.vnet.module.subnet["${local.subnet_name}"].azapi_resource.subnet
                  lifecycle {
                    destroy = false
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldUseLifecycleDestroyTrueWhenConfigured() {
        rewriteRun(
            spec -> spec.recipe(new AddRemovedBlock(
                "module.private_dns_zone.azurerm_private_dns_zone.this",
                true,
                null
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """,
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                removed {
                  from = module.private_dns_zone.azurerm_private_dns_zone.this
                  lifecycle {
                    destroy = true
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesMatchingPattern() {
        rewriteRun(
            spec -> spec.recipe(new AddRemovedBlock(
                "module.private_dns_zone.azurerm_private_dns_zone.this",
                false,
                "**/prod/**/*.tf"
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """,
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                removed {
                  from = module.private_dns_zone.azurerm_private_dns_zone.this
                  lifecycle {
                    destroy = false
                  }
                }
                """,
                sourceSpecs -> sourceSpecs.path("env/prod/main.tf")
            ),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """,
                sourceSpecs -> sourceSpecs.path("env/dev/main.tf")
            )
        );
    }

    @Test
    void shouldThrowExceptionWhenPlaceholderIsNotSet() {
        String propertyName = "avm.removed.missing";

        AddRemovedBlock recipe = new AddRemovedBlock(
            "${" + propertyName + "}",
            false,
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Failed to resolve property placeholders in: '${" + propertyName + "}' (unresolved keys: " + propertyName + ")");
    }

    @Test
    void shouldNotAddDuplicateRemovedBlockWhenAlreadyExists() {
        rewriteRun(
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                removed {
                  from = module.private_dns_zone.azurerm_private_dns_zone.this
                  lifecycle {
                    destroy = false
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyAddRemovedBlockWhenMatchingModuleExists() {
        rewriteRun(
            spec -> spec.recipe(new AddRemovedBlock(
                "private_dns_zone",
                "Azure/avm-res-network-privatednszone/azurerm",
                "0.3.5",
                "module.private_dns_zone.azurerm_private_dns_zone.this",
                false,
                null
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }
                """,
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }

                removed {
                  from = module.private_dns_zone.azurerm_private_dns_zone.this
                  lifecycle {
                    destroy = false
                  }
                }
                """
            ),
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.4.0"
                }
                """
            )
        );
    }

    @Test
    void shouldResolveModuleFilterPlaceholders() {
        String moduleNameKey = "avm.removed.filter.moduleName";
        String sourceKey = "avm.removed.filter.source";
        String versionKey = "avm.removed.filter.version";
        String moduleNamePrevious = System.getProperty(moduleNameKey);
        String sourcePrevious = System.getProperty(sourceKey);
        String versionPrevious = System.getProperty(versionKey);

        System.setProperty(moduleNameKey, "private_dns_zone");
        System.setProperty(sourceKey, "Azure/avm-res-network-privatednszone/azurerm");
        System.setProperty(versionKey, "0.3.5");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddRemovedBlock(
                    "${avm.removed.filter.moduleName}",
                    "${avm.removed.filter.source}",
                    "${avm.removed.filter.version}",
                    "module.private_dns_zone.azurerm_private_dns_zone.this",
                    false,
                    null
                )),
                hcl(
                    """
                    module "private_dns_zone" {
                      source  = "Azure/avm-res-network-privatednszone/azurerm"
                      version = "0.3.5"
                    }
                    """,
                    """
                    module "private_dns_zone" {
                      source  = "Azure/avm-res-network-privatednszone/azurerm"
                      version = "0.3.5"
                    }

                    removed {
                      from = module.private_dns_zone.azurerm_private_dns_zone.this
                      lifecycle {
                        destroy = false
                      }
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
    void shouldNotAddDuplicateRemovedBlockWhenBodyItemsHaveDifferentOrder() {
        rewriteRun(
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                removed {
                  lifecycle {
                    destroy = false
                  }
                  from = module.private_dns_zone.azurerm_private_dns_zone.this
                }
                """
            )
        );
    }

    @Test
    void shouldThrowExceptionWhenPlaceholderResolvesToBlank() {
        String fromProperty = "avm.removed.from.blank";
        String previousValue = System.getProperty(fromProperty);

        System.setProperty(fromProperty, "   ");

        try {
            AddRemovedBlock recipe = new AddRemovedBlock(
                "${" + fromProperty + "}",
                false,
                null
            );

            assertThatThrownBy(() -> rewriteRun(
                spec -> spec.recipe(recipe),
                hcl(
                    """
                    module "private_dns_zone" {
                      source = "Azure/avm-res-network-privatednszone/azurerm"
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
            restoreSystemProperty(fromProperty, previousValue);
        }
    }

    @ParameterizedTest(name = "should reject invalid from=''{0}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      ""    | 'from' must be specified and cannot be empty.
      " "   | 'from' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidFromOptions(String from, String expectedMessage) {
        AddRemovedBlock recipe = new AddRemovedBlock(from, false, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest(name = "should reject blank module filters moduleName=''{0}'' source=''{1}'' version=''{2}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      " " | value | 1.0.0 | 'moduleName' cannot be empty when specified.
      value | " " | 1.0.0 | 'source' cannot be empty when specified.
      value | value | " " | 'version' cannot be empty when specified.
        """)
    void shouldRejectBlankOptionalModuleFilters(String moduleName, String source, String version, String expectedMessage) {
        AddRemovedBlock recipe = new AddRemovedBlock(
            moduleName,
            source,
            version,
            "module.private_dns_zone.azurerm_private_dns_zone.this",
            false,
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void shouldThrowExceptionWhenFromIsQuotedStringLiteral() {
        AddRemovedBlock recipe = new AddRemovedBlock(
            "\"module.private_dns_zone\"",
            false,
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("'from' must be an HCL traversal reference (e.g. module.foo) but looks like a quoted string literal: \"module.private_dns_zone\"");
    }

    @Test
    void shouldThrowExceptionWhenFromDoesNotStartWithIdentifier() {
        AddRemovedBlock recipe = new AddRemovedBlock(
            "0module.private_dns_zone",
            false,
            null
        );

        assertThatThrownBy(() -> rewriteRun(
            spec -> spec.recipe(recipe),
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }
                """
            )
        ))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("'from' must be an HCL traversal reference starting with a letter or underscore, but got: 0module.private_dns_zone");
    }
}
