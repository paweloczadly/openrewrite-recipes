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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openrewrite.hcl.Assertions.hcl;

class ChangeProviderVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", null, "~> 6.0", null));
    }

    @DocumentExample
    @Test
    void shouldChangeProviderVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyChangeMatchingProviderAmongMultipleProviders() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_version = ">= 1.5.0"

                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                    kubernetes = {
                      source  = "hashicorp/kubernetes"
                      version = "~> 2.30"
                    }
                    helm = {
                      source  = "hashicorp/helm"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_version = ">= 1.5.0"

                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                    kubernetes = {
                      source  = "hashicorp/kubernetes"
                      version = "~> 2.30"
                    }
                    helm = {
                      source  = "hashicorp/helm"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldPreserveOtherAttributesInProviderEntry() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source                = "hashicorp/aws"
                      version               = "~> 5.0"
                      configuration_aliases = [aws.us_east_1]
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source                = "hashicorp/aws"
                      version               = "~> 6.0"
                      configuration_aliases = [aws.us_east_1]
                    }
                  }
                }
                """
            )
        );
    }

    @ParameterizedTest(name = "should change provider version when filter constraint is ''{0}'' and provider version is ''{1}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      "~> 5.0"          | "~> 5.0"
      "~> 5.0"          | "~> 5.82"
      "~> 5.0"          | "5.82.1"
      ">= 5.0, < 6.0"   | ">= 5.40"
      "5.0"             | "= 5.0"
      "~> 5.0"          | "< 5.0"
      """)
    void shouldChangeProviderVersionWhenVersionFilterMatches(String providerVersion, String currentVersion) {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", providerVersion, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "%s"
                    }
                  }
                }
                """.formatted(currentVersion),
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @ParameterizedTest(name = "should not change provider version ''{1}'' for filter constraint ''{0}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      "~> 5.0" | "~> 4.67"
      "~> 5.0" | "~> 6.1"
      "~> 5.0" | ">= 5.0, < 6.0"
      "~> 5.0" | "!= 5.1.0"
      "~> 5.0" | "> 4.0"
      "~> 5.0" | ""
      """)
    void shouldNotChangeProviderVersionWhenVersionFilterDoesNotMatch(String providerVersion, String currentVersion) {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", providerVersion, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "%s"
                    }
                  }
                }
                """.formatted(currentVersion)
            )
        );
    }

    @Test
    void shouldNotChangeWhenAlreadyAtNewVersion() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", "~> 6.0", "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotReformatCodeOutsideTheVersionAttribute() {
        rewriteRun(
            hcl(
                """
                locals {
                  settings = {
                    enabled = true
                    # A comment between attributes ends terraform fmt's alignment group.
                    retention_in_days = 30
                  }
                }

                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                locals {
                  settings = {
                    enabled = true
                    # A comment between attributes ends terraform fmt's alignment group.
                    retention_in_days = 30
                  }
                }

                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyChangeProviderWithMatchingName() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion("aws", "hashicorp/aws", null, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                    aws_legacy = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                    aws_legacy = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldMatchSourceExactly() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "registry.terraform.io/hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeProviderEntryWithoutVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source = "hashicorp/aws"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldChangeObjectEntryWithoutSource() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeObjectEntryWithoutSourceOutsideTheHashicorpNamespace() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "integrations/github", null, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    github = {
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeObjectEntryWithInterpolatedVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> ${var.aws_major}.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldChangeShorthandProviderEntry() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = ">= 5.0.0"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = "~> 6.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldChangeShorthandAndObjectEntriesInOneBlock() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/helm", null, "3.1.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws  = "~> 5.0"
                    helm = "~> 2.17"
                    kubectl = {
                      source  = "gavinbunney/kubectl"
                      version = "~> 1.19"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws  = "~> 5.0"
                    helm = "3.1.0"
                    kubectl = {
                      source  = "gavinbunney/kubectl"
                      version = "~> 1.19"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyChangeShorthandEntryWithMatchingName() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion("aws", "hashicorp/aws", null, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws     = "~> 5.0"
                    aws_old = {
                      source  = "hashicorp/aws"
                      version = "~> 4.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws     = "~> 6.0"
                    aws_old = {
                      source  = "hashicorp/aws"
                      version = "~> 4.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeShorthandEntryOutsideTheHashicorpNamespace() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "integrations/github", null, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    github = "~> 5.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldChangeShorthandEntryWhenVersionFilterMatches() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", "~> 5.0", "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = "~> 5.82"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = "~> 6.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeShorthandEntryWhenVersionFilterDoesNotMatch() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", "~> 5.0", "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = "~> 4.67"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeShorthandEntryWithDynamicVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = var.aws_provider_version
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeProviderConfigurationBlocksOrModules() {
        rewriteRun(
            hcl(
                """
                provider "aws" {
                  region  = "eu-central-1"
                  version = "~> 5.0"
                }

                module "vpc" {
                  source  = "hashicorp/aws"
                  version = "~> 5.0"
                }

                locals {
                  required_providers = {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesMatchingPattern() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", null, "~> 6.0", "**/prod/**/*.tf")),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """,
                spec -> spec.path("env/prod/versions.tf")
            ),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                spec -> spec.path("env/dev/versions.tf")
            )
        );
    }

    @Test
    void shouldResolvePlaceholderInNewVersion() {
        String key = "dv.aws.provider.version";
        String previousValue = System.getProperty(key);
        System.setProperty(key, "~> 6.0");

        try {
            rewriteRun(
                spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", null, "${dv.aws.provider.version}", null)),
                hcl(
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "~> 5.0"
                        }
                      }
                    }
                    """,
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "~> 6.0"
                        }
                      }
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

    @Test
    void shouldChangeProviderWithHyphenatedName() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/google-beta", null, "~> 8.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    google-beta = {
                      source  = "hashicorp/google-beta"
                      version = "~> 7.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    google-beta = {
                      source  = "hashicorp/google-beta"
                      version = "~> 8.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldChangeShorthandEntryWithHyphenatedName() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/google-beta", null, "~> 8.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    google-beta = "~> 7.0"
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    google-beta = "~> 8.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyChangeProviderWithMatchingHyphenatedName() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion("google-beta", "hashicorp/google-beta", null, "~> 8.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    google = {
                      source  = "hashicorp/google-beta"
                      version = "~> 7.0"
                    }
                    google-beta = {
                      source  = "hashicorp/google-beta"
                      version = "~> 7.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    google = {
                      source  = "hashicorp/google-beta"
                      version = "~> 7.0"
                    }
                    google-beta = {
                      source  = "hashicorp/google-beta"
                      version = "~> 8.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeEntryWhoseExplicitSourceDiffersFromImpliedSource() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "acme/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeRequiredProvidersOutsideTerraformBlock() {
        rewriteRun(
            hcl(
                """
                required_providers {
                  aws = {
                    source  = "hashicorp/aws"
                    version = "~> 5.0"
                  }
                }

                module "x" {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeNestedTerraformBlock() {
        rewriteRun(
            hcl(
                """
                wrapper {
                  terraform {
                    required_providers {
                      aws = {
                        source  = "hashicorp/aws"
                        version = "~> 5.0"
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeNonTfFilesByDefault() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                spec -> spec.path("env/prod/terragrunt.hcl")
            )
        );
    }

    @Test
    void shouldChangeEmptyVersionWhenNoVersionFilter() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = ""
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeShorthandEntryAlreadyAtNewVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = "~> 6.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeShorthandEntryWithInterpolatedVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = "~> ${var.major}.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeObjectEntryWithVariableVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = var.aws_version
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeObjectEntryWithQuotedKeys() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      "source"  = "hashicorp/aws"
                      "version" = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldResolvePlaceholdersInFilterOptions() {
        String nameKey = "dv.aws.provider.name";
        String sourceKey = "dv.aws.provider.source";
        String versionKey = "dv.aws.provider.filter";
        String previousName = System.getProperty(nameKey);
        String previousSource = System.getProperty(sourceKey);
        String previousVersion = System.getProperty(versionKey);
        System.setProperty(nameKey, "aws");
        System.setProperty(sourceKey, "hashicorp/aws");
        System.setProperty(versionKey, "~> 5.0");

        try {
            ChangeProviderVersion recipe = new ChangeProviderVersion(
                "${dv.aws.provider.name}", "${dv.aws.provider.source}", "${dv.aws.provider.filter}", "~> 6.0", null);
            assertThat(recipe.validate().isValid()).isTrue();
            rewriteRun(
                spec -> spec.recipe(recipe),
                hcl(
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "~> 5.2"
                        }
                      }
                    }
                    """,
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "~> 6.0"
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

    @ParameterizedTest(name = "should fail when ''{0}'' is ''{1}'' and the property is ''{2}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', nullValues = "null", textBlock = """
      newVersion      | ${undefined.property} | null     | Failed to resolve property placeholders in: '${undefined.property}' (unresolved keys: undefined.property)
      newVersion      | ${blank.property}     | " "      | Placeholder '${blank.property}' for 'newVersion' resolved to an empty or blank value
      providerVersion | ${bad.property}       | ">= 5.x" | 'providerVersion' must be a valid semantic version constraint.
      """)
    void shouldFailWhenPlaceholderCannotBeResolved(String fieldName,
                                                   String placeholder,
                                                   String propertyValue,
                                                   String expectedMessage) {
        String key = placeholder.substring(2, placeholder.length() - 1);
        String previousValue = System.getProperty(key);
        if (propertyValue != null) {
            System.setProperty(key, propertyValue);
        }

        try {
            ChangeProviderVersion recipe = "newVersion".equals(fieldName)
                ? new ChangeProviderVersion(null, "hashicorp/aws", null, placeholder, null)
                : new ChangeProviderVersion(null, "hashicorp/aws", placeholder, "~> 6.0", null);

            assertThatThrownBy(recipe::getVisitor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(expectedMessage);
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

    @ParameterizedTest(name = "should reject providerName=''{0}'' providerSource=''{1}'' providerVersion=''{2}'' newVersion=''{3}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', nullValues = "null", textBlock = """
      " "   | hashicorp/aws | null      | "~> 6.0" | 'providerName' cannot be empty when specified.
      1aws  | hashicorp/aws | null      | "~> 6.0" | 'providerName' must be a valid provider local name matching [A-Za-z_][A-Za-z0-9_-]*.
      aws   | null          | null      | "~> 6.0" | 'providerSource' must be specified and cannot be empty.
      aws   | " "           | null      | "~> 6.0" | 'providerSource' must be specified and cannot be empty.
      aws   | hashicorp/aws | " "       | "~> 6.0" | 'providerVersion' cannot be empty when specified.
      aws   | hashicorp/aws | ">= 5.x"  | "~> 6.0" | 'providerVersion' must be a valid semantic version constraint.
      aws   | hashicorp/aws | "~> 5.0"  | null     | 'newVersion' must be specified and cannot be empty.
      aws   | hashicorp/aws | "~> 5.0"  | " "      | 'newVersion' must be specified and cannot be empty.
      """)
    void shouldRejectInvalidOptions(String providerName,
                                    String providerSource,
                                    String providerVersion,
                                    String newVersion,
                                    String expectedMessage) {
        ChangeProviderVersion recipe = new ChangeProviderVersion(providerName, providerSource, providerVersion, newVersion, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}
