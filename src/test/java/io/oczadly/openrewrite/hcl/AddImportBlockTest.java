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

public class AddImportBlockTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddImportBlock(
            "module.private_dns_zone.azapi_resource.private_dns_zone",
            "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01",
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldAddImportBlockWithLiteralValues() {
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

                import {
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                  id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01"
                }
                """
            )
        );
    }

    @Test
    void shouldAllowCommentTokensInsideQuotedTraversalKey() {
        rewriteRun(
            spec -> spec.recipe(new AddImportBlock(
                "module.private_dns_zone.module.virtual_network_links[\"a#b\"].azapi_resource.private_dns_zone_network_link",
                "resource-id",
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

                import {
                  to = module.private_dns_zone.module.virtual_network_links["a#b"].azapi_resource.private_dns_zone_network_link
                  id = "resource-id"
                }
                """
            )
        );
    }

    @Test
    void shouldRejectCommentSyntaxOutsideQuotedTraversal() {
        AddImportBlock recipe = new AddImportBlock(
            "module.private_dns_zone#broken.azapi_resource.private_dns_zone",
            "resource-id",
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
            .hasRootCauseMessage("'to' must be a single-line HCL traversal reference but contains comment syntax: module.private_dns_zone#broken.azapi_resource.private_dns_zone");
    }

    @Test
    void shouldAddImportBlockWithPlaceholderInterpolation() {
        String moduleNameKey = "avm.import.moduleName.ref";
        String linkKeyKey = "avm.import.linkKey.ref";
        String subscriptionIdKey = "avm.import.subscriptionId.ref";
        String resourceGroupKey = "avm.import.resourceGroup.ref";
        String zoneNameKey = "avm.import.zoneName.ref";
        String linkNameKey = "avm.import.linkName.ref";

        String moduleNamePreviousValue = System.getProperty(moduleNameKey);
        String linkKeyPreviousValue = System.getProperty(linkKeyKey);
        String subscriptionIdPreviousValue = System.getProperty(subscriptionIdKey);
        String resourceGroupPreviousValue = System.getProperty(resourceGroupKey);
        String zoneNamePreviousValue = System.getProperty(zoneNameKey);
        String linkNamePreviousValue = System.getProperty(linkNameKey);

        System.setProperty(moduleNameKey, "private_dns_zone");
        System.setProperty(linkKeyKey, "my_key1");
        System.setProperty(subscriptionIdKey, "{subscription_id}");
        System.setProperty(resourceGroupKey, "{resource_group_name}");
        System.setProperty(zoneNameKey, "{private_dns_zone_name}");
        System.setProperty(linkNameKey, "{virtual_network_link_name}");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddImportBlock(
                    "module.${avm.import.moduleName.ref}.module.virtual_network_links[\"${avm.import.linkKey.ref}\"].azapi_resource.private_dns_zone_network_link",
                    "/subscriptions/${avm.import.subscriptionId.ref}/resourceGroups/${avm.import.resourceGroup.ref}/providers/Microsoft.Network/privateDnsZones/${avm.import.zoneName.ref}/virtualNetworkLinks/${avm.import.linkName.ref}?api-version=2024-06-01",
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

                    import {
                      to = module.private_dns_zone.module.virtual_network_links["my_key1"].azapi_resource.private_dns_zone_network_link
                      id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}/virtualNetworkLinks/{virtual_network_link_name}?api-version=2024-06-01"
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(moduleNameKey, moduleNamePreviousValue);
            restoreSystemProperty(linkKeyKey, linkKeyPreviousValue);
            restoreSystemProperty(subscriptionIdKey, subscriptionIdPreviousValue);
            restoreSystemProperty(resourceGroupKey, resourceGroupPreviousValue);
            restoreSystemProperty(zoneNameKey, zoneNamePreviousValue);
            restoreSystemProperty(linkNameKey, linkNamePreviousValue);
        }
    }

    @Test
    void shouldPreserveLiteralTerraformInterpolationInTo() {
        rewriteRun(
            spec -> spec.recipe(new AddImportBlock(
                "module.vnet.module.subnet[\"${{local.subnet_name}}\"].azapi_resource.subnet",
                "resource-id",
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

                import {
                  to = module.vnet.module.subnet["${local.subnet_name}"].azapi_resource.subnet
                  id = "resource-id"
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesMatchingPattern() {
        rewriteRun(
            spec -> spec.recipe(new AddImportBlock(
                "module.private_dns_zone.azapi_resource.private_dns_zone",
                "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01",
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

                import {
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                  id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01"
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
    void shouldOnlyAddImportBlockWhenMatchingModuleExists() {
        rewriteRun(
            spec -> spec.recipe(new AddImportBlock(
                "private_dns_zone",
                "Azure/avm-res-network-privatednszone/azurerm",
                "0.3.5",
                "module.private_dns_zone.azapi_resource.private_dns_zone",
                "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01",
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

                import {
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                  id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01"
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
        String moduleNameKey = "avm.import.filter.moduleName";
        String sourceKey = "avm.import.filter.source";
        String versionKey = "avm.import.filter.version";
        String moduleNamePrevious = System.getProperty(moduleNameKey);
        String sourcePrevious = System.getProperty(sourceKey);
        String versionPrevious = System.getProperty(versionKey);

        System.setProperty(moduleNameKey, "private_dns_zone");
        System.setProperty(sourceKey, "Azure/avm-res-network-privatednszone/azurerm");
        System.setProperty(versionKey, "0.3.5");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddImportBlock(
                    "${avm.import.filter.moduleName}",
                    "${avm.import.filter.source}",
                    "${avm.import.filter.version}",
                    "module.private_dns_zone.azapi_resource.private_dns_zone",
                    "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01",
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

                    import {
                      to = module.private_dns_zone.azapi_resource.private_dns_zone
                      id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01"
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
    void shouldThrowExceptionWhenPlaceholderIsNotSet() {
        String missingKey = "avm.import.to.missing";

        AddImportBlock recipe = new AddImportBlock(
            "${" + missingKey + "}",
            "value",
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
                assertThat(root.getMessage()).isEqualTo("Failed to resolve property placeholders in: '${" + missingKey + "}' (unresolved keys: " + missingKey + ")");
            });
    }

    @Test
    void shouldNotAddDuplicateImportBlockWhenAlreadyExists() {
        rewriteRun(
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                import {
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                  id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01"
                }
                """
            )
        );
    }

    @Test
    void shouldNotAddDuplicateImportBlockWhenAttributesHaveDifferentOrder() {
        rewriteRun(
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                import {
                  id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01"
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                }
                """
            )
        );
    }

    @Test
    void shouldNotAddDuplicateImportBlockWhenExistingBlockContainsComments() {
        rewriteRun(
            hcl(
                """
                module "private_dns_zone" {
                  source = "Azure/avm-res-network-privatednszone/azurerm"
                }

                import {
                  # Existing manual comment
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                  id = "/subscriptions/{subscription_id}/resourceGroups/{resource_group_name}/providers/Microsoft.Network/privateDnsZones/{private_dns_zone_name}?api-version=2024-06-01" // inline note
                }
                """
            )
        );
    }

    @Test
    void shouldEscapeControlCharactersInQuotedIdValue() {
        rewriteRun(
            spec -> spec.recipe(new AddImportBlock(
                "module.private_dns_zone.azapi_resource.private_dns_zone",
                "line1\nline2\tvalue",
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

                import {
                  to = module.private_dns_zone.azapi_resource.private_dns_zone
                  id = "line1\\nline2\\tvalue"
                }
                """
            )
        );
    }

    @Test
    void shouldThrowExceptionWhenPlaceholderResolvesToBlank() {
        String toProperty = "avm.import.to.blank";
        String toPreviousValue = System.getProperty(toProperty);

        System.setProperty(toProperty, "   ");

        try {
            AddImportBlock recipe = new AddImportBlock(
                "${" + toProperty + "}",
                "value",
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
                    assertThat(root.getMessage()).isEqualTo("Placeholder '${" + toProperty + "}' for 'to' resolved to an empty or blank value");
                });
        } finally {
            restoreSystemProperty(toProperty, toPreviousValue);
        }
    }

    @ParameterizedTest(name = "should reject invalid to=''{0}'' id=''{1}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        ""    | value | 'to' must be specified and cannot be empty.
        value | ""    | 'id' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidOptions(String to, String id, String expectedMessage) {
        AddImportBlock recipe = new AddImportBlock(to, id, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest(name = "should reject blank module filters moduleName=''{0}'' source=''{1}'' version=''{2}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        " " | value | value | 'moduleName' cannot be empty when specified.
        value | " " | value | 'source' cannot be empty when specified.
        value | value | " " | 'version' cannot be empty when specified.
        """)
    void shouldRejectBlankOptionalModuleFilters(String moduleName, String source, String version, String expectedMessage) {
        AddImportBlock recipe = new AddImportBlock(
            moduleName,
            source,
            version,
            "module.private_dns_zone.azapi_resource.private_dns_zone",
            "resource-id",
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void shouldThrowExceptionWhenToIsQuotedStringLiteral() {
        AddImportBlock recipe = new AddImportBlock(
            "\"module.private_dns_zone\"",
            "value",
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
            .hasRootCauseMessage("'to' must be an HCL traversal reference (e.g. module.foo) but looks like a quoted string literal: \"module.private_dns_zone\"");
    }

    @Test
    void shouldThrowExceptionWhenToDoesNotStartWithIdentifier() {
        AddImportBlock recipe = new AddImportBlock(
            "0module.private_dns_zone",
            "value",
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
            .hasRootCauseMessage("'to' must be an HCL traversal reference starting with a letter or underscore, but got: 0module.private_dns_zone");
    }
}
