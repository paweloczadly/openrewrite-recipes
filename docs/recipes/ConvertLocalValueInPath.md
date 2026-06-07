# Convert local value in path

`io.oczadly.openrewrite.hcl.ConvertLocalValueInPath`

Transform attribute values in nested map structures using path expressions with wildcard support.

When at least one module filter (`moduleName`, `source`, or `version`) is configured, the recipe runs in two phases: it first scans for matching module blocks and then applies locals transformations in files from the same directory. Without module filters, it applies directly to files matching `filePattern` (or `**/*.tf` by default).

It transforms values only inside `locals` blocks. It does not apply to module input arguments, variable defaults, output values, or data blocks.

## Recipe source

[ConvertLocalValueInPath.java](../../src/main/java/io/oczadly/openrewrite/hcl/ConvertLocalValueInPath.java)

## Options

| Type     | Name           | Description                                              | Example                                          |
|----------|----------------|----------------------------------------------------------|--------------------------------------------------|
| `String` | localName      | Name of the locals variable                              | `"txt_records"`                                  |
| `String` | attributePath  | Supported values: `records.*.value`, `*.records.*.value` | `"records.*.value"`                              |
| `String` | transformation | Transformation type: `stringToList`, `listToString`      | `"stringToList"`                                 |
| `String` | source         | *Optional*. Exact match filter for module source         | `"Azure/avm-res-network-privatednszone/azurerm"` |
| `String` | version        | *Optional*. Exact match filter for module version        | `"~> 0.4.0"`                                     |
| `String` | moduleName     | *Optional*. Filter by module name                        | `"my-module"`                                    |
| `String` | filePattern    | *Optional*. Glob pattern to match files                  | `"**/locals.tf"`                                 |

Supported transformations and path scope:
- `stringToList` for `records.*.value` / `*.records.*.value`
- `listToString` for `records.*.value` / `*.records.*.value` (single string element lists only)

Module filters use exact string matching after placeholder resolution.


## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-privatednszone` from 0.3.x to 0.4.0

## Example

The following example demonstrates converting `value` fields from string to list in the `txt_records` locals variable.

| Parameter      | Value                 |
|----------------|-----------------------|
| localName      | `"txt_records"`       |
| attributePath  | `"*.records.*.value"` |
| transformation | `"stringToList"`      |

**Before**

```hcl
locals {
  txt_records = {
    "record1" = {
      records = {
        "a" = {
          value = "apple"
        }
        "b" = {
          value = "banana"
        }
      }
    }
  }
}
```

**After**

```hcl
locals {
  txt_records = {
    "record1" = {
      records = {
        "a" = {
          value = ["apple"]
        }
        "b" = {
          value = ["banana"]
        }
      }
    }
  }
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.ConvertDnsRecordsStringToList`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.ConvertDnsRecordsStringToList
displayName: Convert DNS records from string to list
description: Converts DNS record values from strings to lists for Azure AVM module migration
recipeList:
  - io.oczadly.openrewrite.hcl.ConvertLocalValueInPath:
      localName: txt_records
      attributePath: "*.records.*.value"
      transformation: stringToList
      source: "${avm.module.source:Azure/avm-res-network-privatednszone/azurerm}"
      version: "~> 0.4.0"
      moduleName: "${avm.module.name:private_dns_zones}"
```

Then run Rewrite with system properties to interpolate filter values:

```sh
./gradlew rewriteRun -Davm.module.source='Azure/avm-res-network-privatednszone/azurerm'
```

### List to string example

```hcl
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
```

becomes:

```hcl
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
```

Now that `io.oczadly.avm.migrations.ConvertDnsRecordsStringToList` has been defined, activate it in your build file:

1. Add the following to your **build.gradle.kts** file:

```kotlin title="build.gradle.kts"
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("io.oczadly:openrewrite-recipes:1.5.4")
}

rewrite {
    activeRecipe("io.oczadly.avm.migrations.ConvertDnsRecordsStringToList")
}
```

2. Run `gradle rewriteRun` to run the recipe.


