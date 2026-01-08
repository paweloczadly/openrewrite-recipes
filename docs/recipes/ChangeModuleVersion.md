# Change module version

`io.oczadly.openrewrite.hcl.ChangeModuleVersion`

Changes the version of a Terraform module.

## Recipe source

[ChangeModuleVersion.java](../../src/main/java/io/oczadly/openrewrite/hcl/ChangeModuleVersion.java)

## Options

| Type     | Name        | Description                                                                                               | Example                                          |
|----------|-------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| `String` | source      | The source address of the module block to modify. Can reference local modules or remote registry modules. | `"Azure/avm-res-network-virtualnetwork/azurerm"` |
| `String` | version     | The version of the module block to modify.                                                                | `"~> 1.0.0"`                                     |
| `String` | newVersion  | The new version to set for the module.                                                                    | `"~> 1.1.0"`                                     |
| `String` | moduleName  | *Optional*. The name of the module block to modify.                                                       | `"vnet_eastus2_apps"`                            |
| `String` | filePattern | *Optional*. A glob pattern to match files to apply this recipe to.                                        | `"**/production/**/*.tf""`                       |

## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-virtualnetwork` from 0.10.0 to 0.11.0

## Example

The following example demonstrates changing the version for `avm-res-network-virtualnetwork` module.

| Parameter  | Value                                            |
|------------|--------------------------------------------------|
| source     | `"Azure/avm-res-network-virtualnetwork/azurerm"` |
| version    | `"~> 0.10.0"`                                    |
| newVersion | `"~> 0.11.0"`                                    |

**Before**

```hcl
module "vnet_eastus2_apps" {
    source  = "Azure/avm-res-network-virtualnetwork/azurerm"
    version = "~> 0.10.0"
}
```

**After**

```hcl
module "vnet_eastus2_apps" {
    source  = "Azure/avm-res-network-virtualnetwork/azurerm"
    version = "~> 0.11.0"
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.ChangeVnetVersionFrom010xTo011x`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.ChangeVnetVersionFrom010xTo011x
displayName: avm-res-network-virtualnetwork 0.10.x -> 0.11.x
description: Update avm-res-network-virtualnetwork from 0.10.x to 0.11.x
recipeList:
  - io.oczadly.openrewrite.hcl.ChangeModuleVersion:
      source: "Azure/avm-res-network-virtualnetwork/azurerm"
      version: "~> 0.10.0"
      newVersion: "~> 0.11.0"
```

Now that `io.oczadly.avm.migrations.ChangeVnetVersionFrom010xTo011x` has been defined, activate it in your build file:

1. Add the following to your **build.gradle.kts** file:

```kotlin title="build.gradle.kts"
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("io.oczadly:openrewrite-recipes:1.1.0")
}

rewrite {
    activeRecipe("io.oczadly.avm.migrations.ChangeVnetVersionFrom010xTo011x")
}
```

2. Run `gradle rewriteRun` to run the recipe.
