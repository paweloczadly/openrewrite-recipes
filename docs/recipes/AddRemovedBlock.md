# Add removed block

`io.oczadly.openrewrite.hcl.AddRemovedBlock`

Adds a top-level `removed` block to OpenTofu configuration files.

## Recipe source

[AddRemovedBlock.java](../../src/main/java/io/oczadly/openrewrite/hcl/AddRemovedBlock.java)

## Options

| Type      | Name             | Description                                                                                                                                | Example                                                          |
|-----------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `String`  | from             | Resource reference to remove from state. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`. | `"module.${avm.pdns.module_name}.azurerm_private_dns_zone.this"` |
| `Boolean` | lifecycleDestroy | Value for `lifecycle.destroy` inside the `removed` block. Defaults to `false` when not provided.                                           | `false`                                                          |
| `String`  | moduleName       | *Optional*. Module label filter; block is added only in files that contain a matching `module` block.                                      | `"private_dns_zone"`                                             |
| `String`  | source           | *Optional*. Module source filter; block is added only in files that contain a matching `module` block with this source.                    | `"Azure/avm-res-network-privatednszone/azurerm"`                 |
| `String`  | version          | *Optional*. Module version filter; block is added only in files that contain a matching `module` block with this version.                  | `"~> 0.4.0"`                                                     |
| `String`  | filePattern      | *Optional*. A glob pattern to match files to apply this recipe to.                                                                         | `"**/production/**/*.tf"`                                        |

## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-privatednszone` from 0.3.0 to 0.4.0

## Example

Based on [Azure/terraform-azurerm-avm-res-network-privatednszone v0.4.0 migration notes](https://github.com/Azure/terraform-azurerm-avm-res-network-privatednszone/releases/tag/v0.4.0).

| Parameter        | Value                                                     |
|------------------|-----------------------------------------------------------|
| moduleName       | private_dns_zone                                          |
| source           | `"Azure/avm-res-network-privatednszone/azurerm"`          |
| version          | `"~> 0.4.0"`                                              |
| from             | `"module.private_dns_zone.azurerm_private_dns_zone.this"` |
| lifecycleDestroy | `false`                                                   |

**Before**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "~> 0.4.0"
}
```

**After**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "~> 0.4.0"
}

removed {
  from = module.private_dns_zone.azurerm_private_dns_zone.this
  lifecycle {
    destroy = false
  }
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.RemovePrivateDnsZoneV040`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

Placeholders in `moduleName` and `from` use `${property}` and `${property:default}` syntax. If you need a literal Terraform interpolation (for example `${local.name}`) in generated HCL, escape it as `${{local.name}}` in recipe configuration.

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.RemovePrivateDnsZoneV040
displayName: avm-res-network-privatednszone 0.4.0 removed
description: Add removed block for deprecated private DNS zone resource
recipeList:
  - io.oczadly.openrewrite.hcl.AddRemovedBlock:
      moduleName: "${avm.pdns.module_name:private_dns_zone}"
      source: "Azure/avm-res-network-privatednszone/azurerm"
      version: "~> 0.4.0"
      from: "module.${avm.pdns.module_name:private_dns_zone}.${avm.pdns.removed_resource:azurerm_private_dns_zone.this}"
      lifecycleDestroy: false
```

Alternatively, define literal values without interpolation:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.RemovePrivateDnsZoneV040
displayName: avm-res-network-privatednszone 0.4.0 removed
description: Add removed block for deprecated private DNS zone resource
recipeList:
  - io.oczadly.openrewrite.hcl.AddRemovedBlock:
      moduleName: "private_dns_zone"
      source: "Azure/avm-res-network-privatednszone/azurerm"
      version: "~> 0.4.0"
      from: "module.private_dns_zone.azurerm_private_dns_zone.this"
      lifecycleDestroy: false
```

Then run Rewrite with the system property values to interpolate:

```sh
./gradlew rewriteRun \
  -Davm.pdns.module_name='private_dns_zone' \
  -Davm.pdns.removed_resource='azurerm_private_dns_zone.this'
```

Now that `io.oczadly.avm.migrations.RemovePrivateDnsZoneV040` has been defined, activate it in your build file:

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
    activeRecipe("io.oczadly.avm.migrations.RemovePrivateDnsZoneV040")
}
```

2. Run `gradle rewriteRun` to run the recipe.
