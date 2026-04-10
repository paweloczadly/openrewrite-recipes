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

public class ConvertLocalValueInPathTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertLocalValueInPath(
            null,
            null,
            null,
            "txt_records",
            "records.*.value",
            "stringToList",
            null
        ));
    }

    @DocumentExample
    @Test
    void shouldConvertStringToListInLocalVariable() {
        rewriteRun(
            hcl(
                """
                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldIgnoreFileWhenLocalsVariableNotPresent() {
        rewriteRun(
            hcl(
                """
                locals {
                  other_var = "value"
                }
                """
            )
        );
    }

    @Test
    void shouldRespectFilePattern() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                "**/locals.tf"
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = "test"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = ["test"]
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("infra/locals.tf")
            ),
            hcl(
                """
                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = "test"
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("main.tf")
            )
        );
    }

    @Test
    void shouldTreatBlankResolvedModuleSourceAsAbsent() {
        String key = "convertLocalValueInPath.blankModuleSource";
        String previousValue = System.getProperty(key);
        System.setProperty(key, "");

        try {
            rewriteRun(
                recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                    null,
                    "${" + key + ":}",
                    null,
                    "txt_records",
                    "records.*.value",
                    "stringToList",
                    null
                )),
                hcl(
                    """
                    locals {
                      txt_records = {
                        "record1" = {
                          records = {
                            "a" = {
                              value = "apple"
                            }
                          }
                        }
                      }
                    }
                    """,
                    """
                    locals {
                      txt_records = {
                        "record1" = {
                          records = {
                            "a" = {
                              value = ["apple"]
                            }
                          }
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
    void shouldFailValidationWhenLocalNamePlaceholderResolvesToBlank() {
        String key = "convertLocalValueInPath.blankLocalName";
        String previousValue = System.getProperty(key);
        System.setProperty(key, "");

        try {
            ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
                null,
                null,
                null,
                "${" + key + ":}",
                "records.*.value",
                "stringToList",
                null
            );

            Validated<Object> validated = recipe.validate();

            assertThat(validated.isValid()).isFalse();
            assertThat(validated.failures()).hasSize(1);
            assertThat(validated.failures().getFirst().getMessage())
                .isEqualTo("Placeholder '${" + key + ":}' for 'localName' resolved to an empty or blank value");
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

    @Test
    void shouldFailValidationWhenLocalNameIsNotValidHclIdentifier() {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            null,
            null,
            null,
            "txt-records",
            "records.*.value",
            "stringToList",
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage())
            .isEqualTo("'localName' must be a valid HCL identifier matching [A-Za-z_][A-Za-z0-9_]*.");
    }

    @Test
    void shouldFailValidationWhenResolvedLocalNameIsNotValidHclIdentifier() {
        String key = "convertLocalValueInPath.invalidLocalName";
        String previousValue = System.getProperty(key);
        System.setProperty(key, "txt-records");

        try {
            ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
                null,
                null,
                null,
                "${" + key + "}",
                "records.*.value",
                "stringToList",
                null
            );

            Validated<Object> validated = recipe.validate();

            assertThat(validated.isValid()).isFalse();
            assertThat(validated.failures()).hasSize(1);
            assertThat(validated.failures().getFirst().getMessage())
                .isEqualTo("'localName' must be a valid HCL identifier matching [A-Za-z_][A-Za-z0-9_]*.");
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        ''          | records.*.value | stringToList | 'localName' must be specified and cannot be empty.
        txt_records | ''              | stringToList | 'attributePath' must be specified and cannot be empty.
        txt_records | records.*.value | ''           | 'transformation' must be specified and cannot be empty.
        """)
    void shouldFailValidationWhenRequiredFieldsMissing(String localName,
                                                       String attributePath,
                                                       String transformation,
                                                       String expectedMessage) {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            null,
            null,
            null,
            localName,
            attributePath,
            transformation,
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void shouldFailValidationWhenUnknownTransformationType() {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            null,
            null,
            null,
            "txt_records",
            "records.*.value",
            "unknownTransformation",
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage())
            .isEqualTo("Unknown transformation type. Supported: stringToList, listToString");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        ' ' | value | value | value | 'source' cannot be empty when specified.
        value | ' ' | value | value | 'version' cannot be empty when specified.
        value | value | ' ' | value | 'moduleName' cannot be empty when specified.
        value | value | value | ' ' | 'filePattern' cannot be empty when specified.
        """)
    void shouldRejectBlankOptionalFilters(String source,
                                          String version,
                                          String moduleName,
                                          String filePattern,
                                          String expectedMessage) {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            moduleName,
            source,
            version,
            "txt_records",
            "records.*.value",
            "stringToList",
            filePattern
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void shouldFailValidationWhenAttributePathIsUnsupported() {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            null,
            null,
            null,
            "txt_records",
            "records.*.ttl",
            "stringToList",
            null
        );

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage())
            .isEqualTo("Unsupported attributePath. Supported values: records.*.value, *.records.*.value");
    }

    @Test
    void shouldPassValidationWithRequiredFields() {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            null,
            null,
            null,
            "txt_records",
            "records.*.value",
            "stringToList",
            null
        );

        Validated<Object> validated = recipe.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "stringToList",
        "string-to-list",
        "STRING_TO_LIST"
    })
    void shouldAcceptSupportedTransformationNamingVariants(String transformation) {
        ConvertLocalValueInPath recipe = new ConvertLocalValueInPath(
            null,
            null,
            null,
            "txt_records",
            "records.*.value",
            transformation,
            null
        );

        Validated<Object> validated = recipe.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void shouldRespectModuleSourceFilter() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                "Azure/avm-res-network-privatednszone/azurerm",
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                module "avm-module" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.4.0"
                }

                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = "test"
                        }
                      }
                    }
                  }
                }
                """,
                """
                module "avm-module" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "0.4.0"
                }

                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = ["test"]
                        }
                      }
                    }
                  }
                }
                """
            ),
            hcl(
                """
                module "other-module" {
                  source = "Azure/avm-res-network-virtualnetwork/azurerm"
                }
                """,
                spec -> spec.path("env/dev/main.tf")
            ),
            hcl(
                """
                locals {
                  txt_records = {
                    "record1" = {
                      records = {
                        "a" = {
                          value = "ignored"
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("env/dev/locals.tf")
            )
        );
    }

    @Test
    void shouldTransformMultipleVariables() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "ptr_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  ptr_records = {
                    "record1" = {
                      records = {
                        "ptr" = {
                          value = "192.168.1.1"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  ptr_records = {
                    "record1" = {
                      records = {
                        "ptr" = {
                          value = ["192.168.1.1"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldConvertListToStringInLocalVariable() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "listToString",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldPreserveFormattingWhenConvertingStringToList() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      name = "txt1"
                      records = {
                        "txtrecordA" = {
                          value = "apple" # keep comment
                        }
                      }
                      tags = {
                        env = "prod"
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      name = "txt1"
                      records = {
                        "txtrecordA" = {
                          value = ["apple"] # keep comment
                        }
                      }
                      tags = {
                        env = "prod"
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldPreserveSlashSlashCommentWhenConvertingStringToList() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple" // keep comment
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"] // keep comment
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldPreserveBlockCommentWhenConvertingStringToList() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple" /* keep comment */
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"] /* keep comment */
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldIgnoreBracesInsideCommentsWhenFindingRecordsObjectBoundary() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        # comment with braces { }
                        "txtrecordA" = {
                          // another comment with braces { }
                          value = "apple"
                          /* block comment with braces { } */
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        # comment with braces { }
                        "txtrecordA" = {
                          // another comment with braces { }
                          value = ["apple"]
                          /* block comment with braces { } */
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldIgnoreRecordsMarkerInsideStringLiteral() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    note = "records = { not_an_object = true }"
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    note = "records = { not_an_object = true }"
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldIgnoreRecordsMarkerInsideComments() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    # records = { commented = true }
                    // records = { commented = true }
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    # records = { commented = true }
                    // records = { commented = true }
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyTransformDirectValueAttributesInsideRecordsEntries() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                          nested = {
                            value = "should-stay-string"
                          }
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                          nested = {
                            value = "should-stay-string"
                          }
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldTreatRecordsPathAsBackwardCompatibleAlias() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    records = {
                      "root" = {
                        value = "root-value"
                      }
                    }
                    "txt_record1" = {
                      records = {
                        "nested" = {
                          value = "nested-value"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    records = {
                      "root" = {
                        value = ["root-value"]
                      }
                    }
                    "txt_record1" = {
                      records = {
                        "nested" = {
                          value = ["nested-value"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldRespectWildcardPrefixRecordsPathShape() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "*.records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    records = {
                      "root" = {
                        value = "root-value"
                      }
                    }
                    "txt_record1" = {
                      records = {
                        "nested" = {
                          value = "nested-value"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    records = {
                      "root" = {
                        value = "root-value"
                      }
                    }
                    "txt_record1" = {
                      records = {
                        "nested" = {
                          value = ["nested-value"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotConvertMultiValueListToString() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                null,
                null,
                null,
                "txt_records",
                "records.*.value",
                "listToString",
                null
            )),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple", "banana"]
                        }
                      }
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldTransformLocalsInSameDirectoryWhenModuleFilterMatchesInAnotherFile() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                "private_dns_zones",
                "Azure/avm-res-network-privatednszone/azurerm",
                "~> 0.4.0",
                "txt_records",
                "*.records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.4.0"
                }
                """,
                spec -> spec.path("env/prod/main.tf")
            ),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("env/prod/locals.tf")
            )
        );
    }

    @Test
    void shouldNotTransformLocalsWhenModuleMatchExistsOnlyInDifferentDirectory() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                "private_dns_zones",
                "Azure/avm-res-network-privatednszone/azurerm",
                "~> 0.4.0",
                "txt_records",
                "*.records.*.value",
                "stringToList",
                null
            )),
            hcl(
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.4.0"
                }
                """,
                spec -> spec.path("env/prod/main.tf")
            ),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("env/dev/locals.tf")
            )
        );
    }

    @Test
    void shouldApplyListToStringAcrossTwoFilesInSameDirectory() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                "private_dns_zones",
                "Azure/avm-res-network-privatednszone/azurerm",
                "~> 0.4.0",
                "txt_records",
                "*.records.*.value",
                "listToString",
                null
            )),
            hcl(
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.4.0"
                }
                """,
                spec -> spec.path("env/prod/main.tf")
            ),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("env/prod/locals.tf")
            )
        );
    }

    @Test
    void shouldRespectFilePatternInTwoPhaseModel() {
        rewriteRun(
            recipeSpec -> recipeSpec.recipe(new ConvertLocalValueInPath(
                "private_dns_zones",
                "Azure/avm-res-network-privatednszone/azurerm",
                "~> 0.4.0",
                "txt_records",
                "*.records.*.value",
                "stringToList",
                "**/locals.tf"
            )),
            hcl(
                """
                module "private_dns_zones" {
                  source  = "Azure/avm-res-network-privatednszone/azurerm"
                  version = "~> 0.4.0"
                }
                """,
                spec -> spec.path("env/prod/main.tf")
            ),
            hcl(
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = "apple"
                        }
                      }
                    }
                  }
                }
                """,
                """
                locals {
                  txt_records = {
                    "txt_record1" = {
                      records = {
                        "txtrecordA" = {
                          value = ["apple"]
                        }
                      }
                    }
                  }
                }
                """,
                spec -> spec.path("env/prod/locals.tf")
            )
        );
    }
}


