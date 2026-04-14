# Add moved block

`io.oczadly.openrewrite.hcl.AddMovedBlock`

Adds a top-level `moved` block to OpenTofu configuration files.

## Recipe source

[AddMovedBlock.java](../../src/main/java/io/oczadly/openrewrite/hcl/AddMovedBlock.java)

## Options

| Type     | Name        | Description                                                                                                                            | Example                                                                                                     |
|----------|-------------|----------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `String` | from        | Old resource reference to move from. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`. | `"module.${avm.vnet.module_name}.module.subnet[\"${avm.vnet.subnet_name}\"].azapi_resource.subnet"`         |
| `String` | to          | New resource reference to move to. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`.   | `"module.${avm.vnet.module_name}.module.subnet[\"${avm.vnet.subnet_name}\"].azapi_resource.subnet_ipam[0]"` |
| `String` | moduleName  | *Optional*. Module label filter; block is added only in files that contain a matching `module` block.                                  | `"vnet"`                                                                                                    |
| `String` | source      | *Optional*. Module source filter; block is added only in files that contain a matching `module` block with this source.                | `"Azure/avm-res-network-virtualnetwork/azurerm"`                                                            |
| `String` | version     | *Optional*. Module version filter; block is added only in files that contain a matching `module` block with this version.              | `"~> 0.15.0"`                                                                                               |
| `String` | filePattern | *Optional*. A glob pattern to match files to apply this recipe to.                                                                     | `"**/production/**/*.tf"`                                                                                   |

## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-virtualnetwork` from 0.14.0 to 0.15.0

## Example

Based on [Azure/terraform-azurerm-avm-res-network-virtualnetwork v0.15.0 migration notes](https://github.com/Azure/terraform-azurerm-avm-res-network-virtualnetwork/releases/tag/v0.15.0).

| Parameter  | Value                                                                     |
|------------|---------------------------------------------------------------------------|
| moduleName | `"vnet"`                                                                  |
| source     | `"Azure/avm-res-network-virtualnetwork/azurerm"`                          |
| version    | `"~> 0.15.0"`                                                             |
| from       | `"module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet"`         |
| to         | `"module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]"` |

**Before**

```hcl
module "vnet" {
  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
  version = "~> 0.15.0"
}
```

**After**

```hcl
module "vnet" {
  source  = "Azure/avm-res-network-virtualnetwork/azurerm"
  version = "~> 0.15.0"
}

moved {
  from = module.vnet.module.subnet["subnet01"].azapi_resource.subnet
  to   = module.vnet.module.subnet["subnet01"].azapi_resource.subnet_ipam[0]
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.MoveVnetSubnetIpamV0150`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

Placeholders in `moduleName`, `from` and `to` use `${property}` and `${property:default}` syntax. If you need a literal Terraform interpolation (for example `${local.name}`) in generated HCL, escape it as `${{local.name}}` in recipe configuration.

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.MoveVnetSubnetIpamV0150
displayName: avm-res-network-virtualnetwork 0.15.0 subnet moved
description: Add moved block for subnet resource refactoring
recipeList:
  - io.oczadly.openrewrite.hcl.AddMovedBlock:
      moduleName: "${avm.vnet.module_name:vnet}"
      source: "Azure/avm-res-network-virtualnetwork/azurerm"
      version: "~> 0.15.0"
      from: "module.${avm.vnet.module_name:vnet}.module.subnet[\"${avm.vnet.subnet_name:subnet01}\"].azapi_resource.subnet"
      to: "module.${avm.vnet.module_name:vnet}.module.subnet[\"${avm.vnet.subnet_name:subnet01}\"].azapi_resource.subnet_ipam[0]"
```

Alternatively, define literal values without interpolation:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.MoveVnetSubnetIpamV0150
displayName: avm-res-network-virtualnetwork 0.15.0 subnet moved
description: Add moved block for subnet resource refactoring
recipeList:
  - io.oczadly.openrewrite.hcl.AddMovedBlock:
      moduleName: "vnet"
      source: "Azure/avm-res-network-virtualnetwork/azurerm"
      version: "~> 0.15.0"
      from: "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet"
      to: "module.vnet.module.subnet[\"subnet01\"].azapi_resource.subnet_ipam[0]"
```

Then run Rewrite with the system property values to interpolate:

```sh
./gradlew rewriteRun \
  -Davm.vnet.module_name='vnet' \
  -Davm.vnet.subnet_name='subnet01'
```

Now that `io.oczadly.avm.migrations.MoveVnetSubnetIpamV0150` has been defined, activate it in your build file:

1. Add the following to your **build.gradle.kts** file:

```kotlin title="build.gradle.kts"
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("io.oczadly:openrewrite-recipes:1.5.0")
}

rewrite {
    activeRecipe("io.oczadly.avm.migrations.MoveVnetSubnetIpamV0150")
}
```

2. Run `gradle rewriteRun` to run the recipe.
