# Add module input

`io.oczadly.openrewrite.hcl.AddModuleInput`

Adds a new input argument to OpenTofu module blocks.

## Recipe source

[AddModuleInput.java](../../src/main/java/io/oczadly/openrewrite/hcl/AddModuleInput.java)

## Options

| Type     | Name               | Description                                                                                                                                                   | Example                                          |
|----------|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| `String` | source             | The source address of the module block to modify. Can reference local modules or remote registry modules.                                                     | `"Azure/avm-res-network-virtualnetwork/azurerm"` |
| `String` | inputName          | The name of the input variable to be added.                                                                                                                   | `"location"`                                     |
| `String` | inputValue         | The value of the input variable to be added. Either 'inputValue' or 'inputValueProperty' must be specified and cannot be empty.                               | `"eastus2"`                                      |
| `String` | inputValueProperty | System property name containing the value to assign to the input variable. Either 'inputValue' or 'inputValueProperty' must be specified and cannot be empty. | `"avm.vnet.location"`                            |
| `String` | moduleName         | *Optional*. The name of the module block to modify.                                                                                                           | `"vnet_eastus2_apps"`                            |
| `String` | version            | *Optional*. The version of the module block to modify.                                                                                                        | `"~> 1.0.0"`                                     |
| `String` | filePattern        | *Optional*. A glob pattern to match files to apply this recipe to.                                                                                            | `"**/production/**/*.tf""`                       |

## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-virtualnetwork` from 0.10.0 to 0.11.0

## Example

The following example demonstrates adding the `parent_id` input to the `avm-res-network-virtualnetwork` local module.

| Parameter  | Value                                         |
|------------|-----------------------------------------------|
| source     | `"../modules/avm-res-network-virtualnetwork"` |
| inputName  | `"parent_id"`                                 |
| inputValue | `"local.parrent_id"`                          |

**Before**

```hcl
module "local_vnet" {
    source = "../modules/avm-res-network-virtualnetwork"
}
```

**After**

```hcl
module "local_vnet" {
    source    = "../modules/avm-res-network-virtualnetwork"
    parent_id = local.parent_id
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.AddParentIdToVnet011x`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.AddParentIdToVnet011x
displayName: avm-res-network-virtualnetwork 0.11.x parent_id
description: Add parent_id to avm-res-network-virtualnetwork 0.11.x
recipeList:
  - io.oczadly.openrewrite.hcl.AddModuleInput:
      source: "Azure/avm-res-network-virtualnetwork/azurerm"
      version: "~> 0.11.0"
      inputName: "parent_id"
      inputValue: "/subscriptions/${data.azurerm_subscription.current.subscription_id}/resourceGroups/${data.terraform_remote_state.rg_default_eastus.outputs.resource.name}"
```

Now that `io.oczadly.avm.migrations.AddParentIdToVnet011x` has been defined, activate it in your build file:

1. Add the following to your **build.gradle.kts** file:

```kotlin title="build.gradle.kts"
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("io.oczadly:openrewrite-recipes:1.0.0")
}

rewrite {
    activeRecipe("io.oczadly.avm.migrations.AddParentIdToVnet011x")
}
```

2. Run `gradle rewriteRun` to run the recipe.
