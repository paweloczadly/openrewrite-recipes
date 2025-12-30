# Remove module input

`io.oczadly.openrewrite.hcl.RemoveModuleInput`

Removes a specified input variable from a Terraform module block.

## Recipe source

[RemoveModuleInput.java](../../src/main/java/io/oczadly/openrewrite/hcl/RemoveModuleInput.java)

## Options

| Type     | Name        | Description                                                                                               | Example                                          |
|----------|-------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| `String` | source      | The source address of the module block to modify. Can reference local modules or remote registry modules. | `"Azure/avm-res-network-virtualnetwork/azurerm"` |
| `String` | inputName   | The name of the input variable to remove from the module.                                                 | `"location"`                                     |
| `String` | moduleName  | *Optional*. The name of the module block to modify.                                                       | `"vnet_eastus2_apps"`                            |
| `String` | version     | *Optional*. The version of the module block to modify.                                                    | `"~> 1.0.0"`                                     |
| `String` | filePattern | *Optional*. A glob pattern to match files to apply this recipe to.                                        | `"**/production/**/*.tf""`                       |

## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-virtualnetwork` from 0.10.0 to 0.11.0

## Example

The following example demonstrates removing the `resource_group_name` input from the `avm-res-network-virtualnetwork` local module.

| Parameter  | Value                                         |
|------------|-----------------------------------------------|
| source     | `"../modules/avm-res-network-virtualnetwork"` |
| inputName  | `"resource_group_name"`                       |

**Before**

```hcl
module "local_vnet" {
    source              = "../modules/avm-res-network-virtualnetwork"
    resource_group_name = local.resource_group_name
}
```

**After**

```hcl
module "local_vnet" {
    source = "../modules/avm-res-network-virtualnetwork"
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.RemoveResourceGroupNamefromVnet011x`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.RemoveResourceGroupNamefromVnet011x
displayName: avm-res-network-virtualnetwork 0.11.x resource_group_name
description: Removes resource_group_name from avm-res-network-virtualnetwork 0.11.x
recipeList:
  - io.oczadly.openrewrite.hcl.RemoveModuleInput:
      moduleName: vnet
      inputName: resource_group_name
```

Now that `io.oczadly.avm.migrations.RemoveResourceGroupNamefromVnet011x` has been defined, activate it in your build file:

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
    activeRecipe("io.oczadly.avm.migrations.RemoveResourceGroupNamefromVnet011x")
}
```

2. Run `gradle rewriteRun` to run the recipe.
