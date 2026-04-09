package io.oczadly.openrewrite.hcl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Validated;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.oczadly.openrewrite.hcl.utils.SystemPropertyTestSupport.restoreSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.hcl.Assertions.hcl;

class RemoveProviderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveProvider("modtm", true, null, null, null, null));
    }

    @DocumentExample
    @Test
    void shouldRemoveProviderFromRequiredProvidersAndConfiguration() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    modtm = {
                      source  = "azure/modtm"
                      version = "~> 0.3.5"
                    }
                    azurerm = "~> 4.45.1"
                  }
                }

                provider "modtm" {
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldKeepProviderConfigurationWhenConfigured() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider("modtm", false, null, null, null, null)),
            hcl(
                """
                terraform {
                  required_providers {
                    modtm = "~> 0.3.5"
                    azurerm = "~> 4.45.1"
                  }
                }

                provider "modtm" {
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }

                provider "modtm" {
                }
                """
            )
        );
    }

    @Test
    void shouldPreserveFormattingWhenRemovingMultilineProviderObject() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider("azapi", true, null, null, null, null)),
            hcl(
                """
                terraform {
                  required_providers {
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                    azurerm = {
                      source  = "hashicorp/azurerm"
                      version = "~> 4.45.1"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = {
                      source  = "hashicorp/azurerm"
                      version = "~> 4.45.1"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldRemoveProviderConfigurationWithoutLeavingExtraBlankLines() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider("modtm", true, null, null, null, null)),
            hcl(
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }

                provider "modtm" {
                }

                resource "azurerm_resource_group" "rg" {
                  name     = "rg-demo"
                  location = "eastus2"
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }

                resource "azurerm_resource_group" "rg" {
                  name     = "rg-demo"
                  location = "eastus2"
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeFileWhenProviderDoesNotExist() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldRemoveProviderFromRequiredProvidersInAnotherFile() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider(
                "aws",
                true,
                "Azure/avm-res-network-privatednszone/azurerm",
                "~> 0.3.5",
                "private_dns_zones",
                null
            )),
            hcl(
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.3.5"
                }
                """,
                spec -> spec.path("main.tf")
            ),
            hcl(
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                    aws     = "~> 6.0.0"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                spec -> spec.path("terraform.tf")
            )
        );
    }

    @Test
    void shouldApplyOnlyWhenMatchingModuleFiltersAreSatisfied() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider(
                "modtm",
                true,
                "Azure/avm-res-network-privatednszone/azurerm",
                "0.3.5",
                "private_dns_zone",
                null
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }

                terraform {
                  required_providers {
                    modtm = "~> 0.3.5"
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }

                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                spec -> spec.path("matching.tf")
            ),
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.4.0"
                }

                terraform {
                  required_providers {
                    modtm = "~> 0.3.5"
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                spec -> spec.path("other/non-matching.tf")
            )
        );
    }

    @Test
    void shouldApplyWhenTargetFileMatchesFilePattern() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider(
                "modtm",
                true,
                "Azure/avm-res-network-privatednszone/azurerm",
                "0.3.5",
                "private_dns_zone",
                "**/terraform.tf"
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }
                """,
                spec -> spec.path("main.tf")
            ),
            hcl(
                """
                terraform {
                  required_providers {
                    modtm   = "~> 0.3.5"
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                spec -> spec.path("terraform.tf")
            )
        );
    }

    @Test
    void shouldNotApplyWhenNoFileInMatchedDirectoryMatchesFilePattern() {
        rewriteRun(
            spec -> spec.recipe(new RemoveProvider(
                "modtm",
                true,
                "Azure/avm-res-network-privatednszone/azurerm",
                "0.3.5",
                "private_dns_zone",
                "**/terraform.tf"
            )),
            hcl(
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }
                """,
                spec -> spec.path("module.tf")
            ),
            hcl(
                """
                terraform {
                  required_providers {
                    modtm   = "~> 0.3.5"
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                spec -> spec.path("providers.tf")
            )
        );
    }

    @Test
    void shouldResolvePlaceholderInName() {
        String key = "avm.remove.provider";
        String previousValue = System.getProperty(key);
        System.setProperty(key, "modtm");

        try {
            rewriteRun(
                spec -> spec.recipe(new RemoveProvider("${avm.remove.provider}", true, null, null, null, null)),
                hcl(
                    """
                    terraform {
                      required_providers {
                        modtm = "~> 0.3.5"
                      }
                    }
                    """,
                    """
                    terraform {
                      required_providers {
                      }
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        ''  | 'providerName' must be specified and cannot be empty.
        ' ' | 'providerName' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidName(String name, String expectedMessage) {
        RemoveProvider recipe = new RemoveProvider(name, null, null, null, null, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        ' ' | value | value | 'source' cannot be empty when specified.
        value | ' ' | value | 'version' cannot be empty when specified.
        value | value | ' ' | 'moduleName' cannot be empty when specified.
        """)
    void shouldRejectBlankOptionalFilters(String moduleSource, String moduleVersion, String moduleName, String expectedMessage) {
        RemoveProvider recipe = new RemoveProvider("modtm", null, moduleSource, moduleVersion, moduleName, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}

