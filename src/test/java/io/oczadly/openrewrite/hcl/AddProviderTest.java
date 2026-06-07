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

class AddProviderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddProvider(
            "azapi",
            "azure/azapi",
            "~> 2.5.0",
            null,
            null,
            null,
            null,
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldAddProviderWithSourceToExistingRequiredProviders() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldCreateTerraformAndRequiredProvidersWhenMissing() {
        rewriteRun(
            spec -> spec.recipe(new AddProvider(
                "azuread",
                null,
                "~> 3.8.0",
                null,
                null,
                null,
                null,
                null
            )),
            hcl(
                """
                resource "azurerm_resource_group" "rg" {
                  name     = "rg-demo"
                  location = "eastus2"
                }
                """,
                """
                resource "azurerm_resource_group" "rg" {
                  name     = "rg-demo"
                  location = "eastus2"
                }

                terraform {
                  required_providers {
                    azuread = "~> 3.8.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldCreateRequiredProvidersInsideExistingTerraformBlock() {
        rewriteRun(
            spec -> spec.recipe(new AddProvider(
                "azapi",
                "azure/azapi",
                "~> 2.5.0",
                null,
                null,
                null,
                null,
                null
            )),
            hcl(
                """
                terraform {
                  experiments = [module_variable_optional_attrs]
                }
                """,
                """
                terraform {
                  experiments = [module_variable_optional_attrs]
                  required_providers {
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldAddProviderConfigurationWhenRequested() {
        rewriteRun(
            spec -> spec.recipe(new AddProvider(
                "azurerm",
                null,
                "~> 4.45.1",
                """
                features {}
                skip_provider_registration = false
                """,
                null,
                null,
                null,
                null
            )),
            hcl(
                """
                terraform {
                  required_providers {
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }

                provider "azurerm" {
                  features {}
                  skip_provider_registration = false
                }
                """
            )
        );
    }

    @Test
    void shouldNotAddDuplicateProviderEntry() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldApplyOnlyWhenMatchingModuleFiltersAreSatisfied() {
        rewriteRun(
            spec -> spec.recipe(new AddProvider(
                "azapi",
                "azure/azapi",
                "~> 2.5.0",
                null,
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
                """,
                """
                module "private_dns_zone" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.3.5"
                }

                terraform {
                  required_providers {
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
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
                """,
                spec -> spec.path("non-matching.tf")
            )
        );
    }

    @Test
    void shouldAddTwoProvidersSequentiallyForPrivateDnszoneModuleMigration() {
        rewriteRun(
            spec -> spec.recipes(
                new AddProvider(
                    "azapi",
                    "azure/azapi",
                    "~> 2.5.0",
                    null,
                    "Azure/avm-res-network-privatednszone/azurerm",
                    "~> 0.4.0",
                    null,
                    null
                ),
                new AddProvider(
                    "modtm",
                    "azure/modtm",
                    "~> 0.3.5",
                    null,
                    "Azure/avm-res-network-privatednszone/azurerm",
                    "~> 0.4.0",
                    null,
                    null
                )
            ),
            hcl(
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.4.0"
                }

                terraform {
                  required_providers {
                    azurerm = { source = "hashicorp/azurerm", version = "~> 4.0" }
                  }
                }
                """,
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.4.0"
                }

                terraform {
                  required_providers {
                    azurerm = { source  = "hashicorp/azurerm", version = "~> 4.0" }
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                    modtm = {
                      source  = "azure/modtm"
                      version = "~> 0.3.5"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldUpdateExistingRequiredProvidersInAnotherFileAndAvoidDuplicateBlock() {
        rewriteRun(
            spec -> spec.recipe(new AddProvider(
                "azapi",
                "azure/azapi",
                "~> 2.5.0",
                null,
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
                """,
                spec -> spec.path("module.tf")
            ),
            hcl(
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                  }
                }
                """,
                spec -> spec.path("main.tf")
            )
        );
    }

    @Test
    void shouldApplyWhenTargetFileMatchesFilePattern() {
        rewriteRun(
            spec -> spec.recipe(new AddProvider(
                "azapi",
                "azure/azapi",
                "~> 2.5.0",
                null,
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
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azurerm = "~> 4.45.1"
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
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
            spec -> spec.recipe(new AddProvider(
                "azapi",
                "azure/azapi",
                "~> 2.5.0",
                null,
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
                    azurerm = "~> 4.45.1"
                  }
                }
                """,
                spec -> spec.path("providers.tf")
            )
        );
    }


    @Test
    void shouldCreateMultilineObjectEntryWhenProviderSourceIsConfigured() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    azapi = {
                      source  = "azure/azapi"
                      version = "~> 2.5.0"
                    }
                  }
                }
                """
            )
        );
    }


    @Test
    void shouldResolvePlaceholdersInProviderOptions() {
        String nameKey = "avm.provider.name";
        String sourceKey = "avm.provider.source";
        String versionKey = "avm.provider.version";

        String previousName = System.getProperty(nameKey);
        String previousSource = System.getProperty(sourceKey);
        String previousVersion = System.getProperty(versionKey);

        System.setProperty(nameKey, "azapi");
        System.setProperty(sourceKey, "azure/azapi");
        System.setProperty(versionKey, "~> 2.5.0");

        try {
            rewriteRun(
                spec -> spec.recipe(new AddProvider(
                    "${avm.provider.name}",
                    "${avm.provider.source}",
                    "${avm.provider.version}",
                    null,
                    null,
                    null,
                    null,
                    null
                )),
                hcl(
                    """
                    terraform {
                      required_providers {
                      }
                    }
                    """,
                    """
                    terraform {
                      required_providers {
                        azapi = {
                          source  = "azure/azapi"
                          version = "~> 2.5.0"
                        }
                      }
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(nameKey, previousName);
            restoreSystemProperty(sourceKey, previousSource);
            restoreSystemProperty(versionKey, previousVersion);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      ""    | value | 'providerName' must be specified and cannot be empty.
        bad-name | value | 'providerName' must be a valid HCL identifier matching [A-Za-z_][A-Za-z0-9_]*.
      value | ""    | 'providerVersion' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidRequiredOptions(String name, String version, String expectedMessage) {
        AddProvider recipe = new AddProvider(name, null, version, null, null, null, null, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      " " | value | value | 'source' cannot be empty when specified.
      value | " " | value | 'version' cannot be empty when specified.
      value | value | " " | 'moduleName' cannot be empty when specified.
        """)
    void shouldRejectBlankOptionalFilters(String moduleSource, String moduleVersion, String moduleName, String expectedMessage) {
        AddProvider recipe = new AddProvider("azapi", "azure/azapi", "~> 2.5.0", null, moduleSource, moduleVersion, moduleName, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}

