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

public class ChangeModuleVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeModuleVersion(
            "avm-res-network-virtualnetwork",
            "Azure/avm-res-network-virtualnetwork/azurerm",
            "0.10.0",
            "0.11.0",
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldChangeModuleVersion() {
        rewriteRun(
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """
            )
        );
    }

    @ParameterizedTest(name = "should change version with constraint ''{0}''")
    @CsvSource(delimiter = '|', textBlock = """
        =     | = 0.10.0   | = 0.11.0
        !=    | != 0.10.0  | != 0.11.0
        >=    | >= 0.10.0  | >= 0.11.0
        >     | > 0.10.0   | > 0.11.0
        <=    | <= 0.10.0  | <= 0.11.0
        <     | < 0.10.0   | < 0.11.0
        ~>    | ~> 0.10.0  | ~> 0.11.0
        ~>    | ~> 0.10    | ~> 0.11
        """)
    void shouldHandleVersionConstraints(String constraint, String version, String newVersion) {
        rewriteRun(
            spec -> spec.recipe(new ChangeModuleVersion(
                null,
                "Azure/avm-res-network-virtualnetwork/azurerm",
                version,
                newVersion,
                null
            )),
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "%s"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """.formatted(version),
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "%s"

                  address_space       = ["10.0.0.0/16"]
                  location            = "eastus2"
                  resource_group_name = "myResourceGroup"
                }
                """.formatted(newVersion)
            )
        );
    }

    @Test
    void shouldNotModifyOtherModules() {
        rewriteRun(
            hcl(
                """
                module "avm-res-network-networksecuritygroup" {
                  source  = "Azure/avm-res-network-networksecuritygroup/azurerm"
                  version = "0.5.0"
                }

                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"
                }
                """,
                """
                module "avm-res-network-networksecuritygroup" {
                  source  = "Azure/avm-res-network-networksecuritygroup/azurerm"
                  version = "0.5.0"
                }

                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesMatchingPattern() {
        rewriteRun(
            spec -> spec.recipe(new ChangeModuleVersion(
                null,
                "Azure/avm-res-network-virtualnetwork/azurerm",
                "0.10.0",
                "0.11.0",
                "**/prod/**/*.tf"
            )),
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"
                }
                """,
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.11.0"
                }
                """,
                spec -> spec.path("env/prod/main.tf")
            ),
            hcl(
                """
                module "avm-res-network-virtualnetwork" {
                  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
                  version = "0.10.0"
                }
                """,
                spec -> spec.path("env/dev/main.tf")
            )
        );
    }

    @ParameterizedTest(name = "should reject invalid version=''{0}'' newVersion=''{1}''")
    @CsvSource(delimiter = '|', textBlock = """
        ''   | value | 'version' must be specified and cannot be empty.
        ' '  | value | 'version' must be specified and cannot be empty.
        name | ''    | 'newVersion' must be specified and cannot be empty.
        name | ' '   | 'newVersion' must be specified and cannot be empty.
        """)
    void shouldRejectInvalidOldVersionAndNewVersion(String version, String newVersion, String expectedMessage) {
        ChangeModuleVersion recipe = new ChangeModuleVersion(
            null,
            "Azure/avm-res-network-virtualnetwork/azurerm",
            version,
            newVersion,
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}
