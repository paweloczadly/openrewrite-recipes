# Add import block

`io.oczadly.openrewrite.hcl.AddImportBlock`

Adds a top-level `import` block to OpenTofu configuration files.

## Recipe source

[AddImportBlock.java](../../src/main/java/io/oczadly/openrewrite/hcl/AddImportBlock.java)

## Options

| Type     | Name        | Description                                                                                                                                | Example                                                                                                                                                                                            |
|----------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `String` | to          | Resource reference to import into state. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`. | `"module.${avm.pdns.module_name}.azapi_resource.private_dns_zone"`                                                                                                                                 |
| `String` | id          | Resource ID to import. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`.                   | `"/subscriptions/${avm.pdns.subscription_id}/resourceGroups/${avm.pdns.resource_group_name}/providers/Microsoft.Network/privateDnsZones/${avm.pdns.private_dns_zone_name}?api-version=2024-06-01"` |
| `String` | moduleName  | *Optional*. Module label filter; block is added only in files that contain a matching `module` block.                                      | `"private_dns_zone"`                                                                                                                                                                               |
| `String` | source      | *Optional*. Module source filter; block is added only in files that contain a matching `module` block with this source.                    | `"Azure/avm-res-network-privatednszone/azurerm"`                                                                                                                                                   |
| `String` | version     | *Optional*. Module semantic version constraint filter; block is added only in files that contain a matching `module` block.                | `"~> 0.4.0"`                                                                                                                                                                                       |
| `String` | filePattern | *Optional*. A glob pattern to match files to apply this recipe to.                                                                         | `"**/prod/**/*.tf"`                                                                                                                                                                                |

## Version filter semantics

When `version` is specified, it is interpreted as a semantic version constraint. The MVP matcher supports `=`, `!=`, `>`, `>=`, `<`, `<=`, `~>`, and comma-separated AND constraints such as `>= 0.10.0, < 0.11.0`. Module blocks match when their `version` attribute is either a concrete stable version literal such as `0.10.2`, or a single operator-prefixed value such as `~> 0.10.2` where the operator is stripped and the numeric portion is matched. Missing, dynamic/interpolated, invalid, and multi-clause module version values do not match.

## Used by

This recipe is commonly used as part of the following composite recipes:

* Migrate `avm-res-network-privatednszone` from 0.3.5 to 0.4.0

## Example

Based on [Azure/terraform-azurerm-avm-res-network-privatednszone v0.4.0 migration notes](https://github.com/Azure/terraform-azurerm-avm-res-network-privatednszone/releases/tag/v0.4.0).

| Parameter  | Value                                                                                                                                                                    |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| moduleName | `"private_dns_zone"`                                                                                                                                                     |
| source     | `"Azure/avm-res-network-privatednszone/azurerm"`                                                                                                                         |
| version    | `"~> 0.4.0"`                                                                                                                                                             |
| to         | `"module.private_dns_zone.azapi_resource.private_dns_zone"`                                                                                                              |
| id         | `"/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001/providers/Microsoft.Network/privateDnsZones/oczadly.io?api-version=2024-06-01"` |

**Before**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "0.4.0"
}
```

**After**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "0.4.0"
}

import {
  to = module.private_dns_zone.azapi_resource.private_dns_zone
  id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001/providers/Microsoft.Network/privateDnsZones/oczadly.io?api-version=2024-06-01"
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.avm.migrations.ImportPrivateDnsZoneV040`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

Placeholders in `moduleName`, `to` and `id` use `${property}` and `${property:default}` syntax. If you need a literal Terraform interpolation (for example `${local.name}`) in generated HCL, escape it as `${{local.name}}` in recipe configuration.

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.ImportPrivateDnsZoneV040
displayName: avm-res-network-privatednszone 0.4.0 import
description: Add import block for private DNS zone resource
recipeList:
  - io.oczadly.openrewrite.hcl.AddImportBlock:
      moduleName: "${avm.pdns.module_name:private_dns_zone}"
      source: "Azure/avm-res-network-privatednszone/azurerm"
      version: "~> 0.4.0"
      to: "module.${avm.pdns.module_name:private_dns_zone}.azapi_resource.private_dns_zone"
      id: "/subscriptions/${avm.pdns.subscription_id}/resourceGroups/${avm.pdns.resource_group_name}/providers/Microsoft.Network/privateDnsZones/${avm.pdns.private_dns_zone_name}?api-version=2024-06-01"
```

Alternatively, define literal values without interpolation:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.ImportPrivateDnsZoneV040
displayName: avm-res-network-privatednszone 0.4.0 import
description: Add import block for private DNS zone resource
recipeList:
  - io.oczadly.openrewrite.hcl.AddImportBlock:
      moduleName: "private_dns_zone"
      source: "Azure/avm-res-network-privatednszone/azurerm"
      version: "~> 0.4.0"
      to: "module.private_dns_zone.azapi_resource.private_dns_zone"
      id: "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-demo-eastus2-001/providers/Microsoft.Network/privateDnsZones/oczadly.io?api-version=2024-06-01"
```

Then run Rewrite with the system property values to interpolate:

```sh
./gradlew rewriteRun \
  -Davm.pdns.module_name='private_dns_zone' \
  -Davm.pdns.subscription_id='00000000-0000-0000-0000-000000000000' \
  -Davm.pdns.resource_group_name='rg-demo-eastus2-001' \
  -Davm.pdns.private_dns_zone_name='oczadly.io'
```

Now that `io.oczadly.avm.migrations.ImportPrivateDnsZoneV040` has been defined, activate it in your build file:

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
    activeRecipe("io.oczadly.avm.migrations.ImportPrivateDnsZoneV040")
}
```

2. Run `gradle rewriteRun` to run the recipe.
