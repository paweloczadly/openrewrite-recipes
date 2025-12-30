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

public class AddModuleInputTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddModuleInput(
            "avm-res-network-virtualnetwork",
            "Azure/avm-res-network-virtualnetwork/azurerm",
            "0.10.0",
            "parent_id",
            "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldAddInputWithLiteralValueOnly() {
        rewriteRun(
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  providers = {
                    azurerm = azurerm.eastus2
                  }

                  # Required inputs:
                  address_space = ["10.0.0.0/16"]
                  location = "eastus2"
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
                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                  parent_id           = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
                }
                """
            )
        );
    }

    @Test
    void shouldAddInputToAProperModuleToAProperFile() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new AddModuleInput(
                "avm-res-network-virtualnetwork",
                "Azure/avm-res-network-virtualnetwork/azurerm",
                null,
                "parent_id",
                "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
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
                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"

                  # Optional inputs:
                  tags = {
                    Environment = "Production"
                    Department  = "Platform Engineering"
                  }
                  parent_id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
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
                  address_space       = ["10.2.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"

                  # Optional inputs:
                  tags = {
                    Environment = "Production"
                    Department  = "Platform Engineering"
                  }
                  parent_id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
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

                sourceSpecs -> sourceSpecs.path("05-networking-vnets/your-subscription/rg-default-eastus/vnet-3/vnet-3.tf")
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesInSpecificFolder() {
        rewriteRun(
            spec -> spec.recipe(new AddModuleInput(
                null,
                "../modules/avm-res-network-virtualnetwork",
                null,
                "parent_id",
                "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
                "**/rg-prod-eastus/**/*.tf"
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source = "../modules/avm-res-network-virtualnetwork"
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source    = "../modules/avm-res-network-virtualnetwork"
                  parent_id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-prod-eastus/vnet-default-eastus/main.tf")
            ),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source = "../modules/avm-res-network-virtualnetwork"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-dev-eastus/vnet-default-eastus/main.tf")
            )
        );
    }

    @Test
    void shouldOnlyModifyMainTfFiles() {
        rewriteRun(
            spec -> spec.recipe(new AddModuleInput(
                null,
                "../modules/avm-res-network-virtualnetwork",
                null,
                "parent_id",
                "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
                "**/main.tf"
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source = "../modules/avm-res-network-virtualnetwork"
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  # Reference to local module:
                  source    = "../modules/avm-res-network-virtualnetwork"
                  parent_id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-prod-eastus/vnet-default-eastus/main.tf")
            ),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source = "Azure/avm-res-network-virtualnetwork/azurerm"
                }
                """,
                spec -> spec.path("05-networking-vnets/your-subscription/rg-prod-eastus/vnet-default-eastus/network.tf")
            )
        );
    }

    @Test
    void shouldNotOverrideExistingInput() {
        rewriteRun(
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                  parent_id           = "/subscriptions/11111111-1111-1111-1111-111111111111/resourceGroups/rg-demo-eastus2-111"
                }
                """
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
                  source  = "local/vnet-custom-module"
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

    @Test
    void shouldOnlyModifyModulesWithSpecificSourceAndVersion() {
        rewriteRun(
            spec -> spec.recipe(new AddModuleInput(
                "avm-res-network-virtualnetwork-0",
                "Azure/avm-res-network-virtualnetwork/azurerm",
                "0.10.0",
                "parent_id",
                "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
                null
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork-0" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }

                module "avm-res-network-virtualnetwork-1" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"

                  address_space       = ["10.1.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }

                module "avm-res-network-virtualnetwork-2" {
                  source  = "local/custom-module"
                  version = "0.10.0"

                  address_space       = ["10.2.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """,
                """
                module "avm-res-network-virtualnetwork-0" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                  parent_id           = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
                }

                module "avm-res-network-virtualnetwork-1" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"

                  address_space       = ["10.1.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }

                module "avm-res-network-virtualnetwork-2" {
                  source  = "local/custom-module"
                  version = "0.10.0"

                  address_space       = ["10.2.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyModulesWithSpecificSource() {
        rewriteRun(
            spec -> spec.recipe(new AddModuleInput(
                "avm-res-network-virtualnetwork-0",
                "Azure/avm-res-network-virtualnetwork/azurerm",
                null,
                "parent_id",
                "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
                null
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork-0" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space = ["10.0.0.0/16"]
                }

                module "avm-res-network-virtualnetwork-1" {
                  source  = "local/custom-module"

                  address_space = ["10.1.0.0/16"]
                }
                """,
                """
                module "avm-res-network-virtualnetwork-0" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space = ["10.0.0.0/16"]
                  parent_id     = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
                }

                module "avm-res-network-virtualnetwork-1" {
                  source  = "local/custom-module"

                  address_space = ["10.1.0.0/16"]
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyModulesWithSpecificVersion() {
        rewriteRun(
            spec -> spec.recipe(new AddModuleInput(
                null,
                "Azure/avm-res-network-virtualnetwork/azurerm",
                "0.10.0",
                "parent_id",
                "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001",
                null
            )),

            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space = ["10.0.0.0/16"]
                }

                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"

                  address_space = ["10.1.0.0/16"]
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space = ["10.0.0.0/16"]
                  parent_id     = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001"
                }

                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"

                  address_space = ["10.1.0.0/16"]
                }
                """
            )
        );
    }

    @ParameterizedTest(name = "should reject invalid inputName=''{0}'' inputValue=''{1}''")
    @CsvSource(delimiter = '|', textBlock = """
        ''   | value | 'inputName' must be specified and cannot be empty.
        ' '  | value | 'inputName' must be specified and cannot be empty.
        name | ''    | 'inputValue' must be specified and cannot be empty.
        name | ' '   | 'inputValue' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidInputNameAndInputValue(String inputName, String inputValue, String expectedMessage) {
        AddModuleInput recipe = new AddModuleInput(
            null,
            "Azure/avm-res-network-virtualnetwork/azurerm",
            null,
            inputName,
            inputValue,
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}
