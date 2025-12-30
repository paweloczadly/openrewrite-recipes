package io.oczadly.openrewrite.hcl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Validated;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.hcl.Assertions.hcl;

public class RemoveModuleInputTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveModuleInput(
            "avm-res-network-virtualnetwork",
            "Azure/avm-res-network-virtualnetwork/azurerm",
            null,
            "resource_group_name",
            "**/*.tf"
        ));
    }

    @DocumentExample
    @Test
    void shouldRemoveModuleInputLiteralValueOnly() {
        rewriteRun(
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  providers = {
                    azurerm = azurerm.eastus2
                  }

                  # Required inputs:
                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """,

                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  providers = {
                    azurerm = azurerm.eastus2
                  }

                  # Required inputs:
                  address_space = ["10.0.0.0/16"]
                  location      = "eastus2"
                }
                """
            )
        );
    }

    @Test
    void shouldRemoveInputToAProperModuleToAProperFile() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new RemoveModuleInput(
                null,
                "Azure/avm-res-network-virtualnetwork/azurerm",
                null,
                "resource_group_name",
                "**/*.tf"
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"

                  # Optional inputs:
                  tags = {
                    Environment = "Production"
                    Department  = "Platform Engineering"
                  }
                }

                module "avm-res-network-virtualnetwork-1" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space       = ["10.1.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """,

                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space = ["10.0.0.0/16"]
                  location      = "eastus2"

                  # Optional inputs:
                  tags = {
                    Environment = "Production"
                    Department  = "Platform Engineering"
                  }
                }

                module "avm-res-network-virtualnetwork-1" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space = ["10.1.0.0/16"]
                  location      = "eastus2"
                }
                """,

                sourceSpecs -> sourceSpecs.path("05-networking-vnets/your-subscription/rg-default-eastus/vnet-1/vnet-1.tf")
            ),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space       = ["10.2.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"

                  # Optional inputs:
                  tags = {
                    Environment = "Production"
                    Department  = "Platform Engineering"
                  }
                }
                """,

                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space = ["10.2.0.0/16"]
                  location      = "eastus2"

                  # Optional inputs:
                  tags = {
                    Environment = "Production"
                    Department  = "Platform Engineering"
                  }
                }
                """,

                sourceSpecs -> sourceSpecs.path("05-networking-vnets/your-subscription/rg-default-eastus/vnet-2/vnet-2.tf")
            ),

            hcl(
                """
                module "avm-res-network-virtualnetwork-2" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space       = ["10.3.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """,

                """
                module "avm-res-network-virtualnetwork-2" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  # Required inputs:
                  address_space = ["10.3.0.0/16"]
                  location      = "eastus2"
                }
                """,

                sourceSpecs -> sourceSpecs.path("05-networking-vnets/your-subscription/rg-default-eastus/vnet-3/vnet-3.tf")
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesInSpecificFolder() {
        rewriteRun(
            spec -> spec.recipe(new RemoveModuleInput(
                null,
                "../modules/avm-res-network-virtualnetwork",
                null,
                "resource_group_name",
                "**/rg-prod-eastus/**/*.tf"
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source              = "../modules/avm-res-network-virtualnetwork"
                  resource_group_name = "myResourceGroup"
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source = "../modules/avm-res-network-virtualnetwork"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-prod-eastus/vnet-default-eastus/main.tf")
            ),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source              = "../modules/avm-res-network-virtualnetwork"
                  resource_group_name = "myResourceGroup"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-dev-eastus/vnet-default-eastus/main.tf")
            )
        );
    }

    @Test
    void shouldNotModifyFileWithoutTargetModule() {
        rewriteRun(
            hcl(
                """
                data "azurerm_location" "location" {
                  location = "East US"
                }

                resource "azurerm_resource_group" "rg" {
                  name     = "myResourceGroup"
                  location = data.azurerm_location.location.name
                }

                module "avm-res-network-virtualnetwork-2" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space       = ["10.2.0.0/16"]
                  location            = data.azurerm_location.location.name
                  resource_group_name = azurerm_resource_group.rg.name
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyMainTfFiles() {
        rewriteRun(
            spec -> spec.recipe(new RemoveModuleInput(
                null,
                "../modules/avm-res-network-virtualnetwork",
                null,
                "resource_group_name",
                "**/main.tf"
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source              = "../modules/avm-res-network-virtualnetwork"
                  resource_group_name = "myResourceGroup"
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source = "../modules/avm-res-network-virtualnetwork"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-prod-eastus/vnet-default-eastus/main.tf")
            ),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source              = "Azure/avm-res-network-virtualnetwork/azurerm"
                  resource_group_name = "myResourceGroup"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-prod-eastus/vnet-default-eastus/network.tf")
            )
        );
    }

    @Test
    void shouldHandleFilesWithoutModules() {
        rewriteRun(
            hcl(
                """
                    output "resource" {
                      value = module.avm-res-network-virtualnetwork.resource
                    }

                    output "subnets" {
                      value = module.avm-res-network-virtualnetwork.subnets
                    }
                    """
            )
        );
    }

    @ParameterizedTest(name = "should reject invalid inputName=''{0}''")
    @CsvSource(delimiter = '|', textBlock = """
        ''   | 'inputName' must be specified and cannot be empty.
        ' '  | 'inputName' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidInputName(String inputName, String expectedMessage) {
        RemoveModuleInput recipe = new RemoveModuleInput(
            null,
            "Azure/avm-res-network-virtualnetwork/azurerm",
            null,
            inputName,
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}
