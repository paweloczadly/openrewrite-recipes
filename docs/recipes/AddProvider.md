# Add provider

`io.oczadly.openrewrite.hcl.AddProvider`

Adds or creates `terraform.required_providers` entries and can append a matching top-level `provider` block.

When multiple `.tf` files exist in one module directory, the recipe first looks for an existing `terraform.required_providers` block in that directory and updates that file to avoid creating duplicate `required_providers` blocks.

## Recipe source

[AddProvider.java](../../src/main/java/io/oczadly/openrewrite/hcl/AddProvider.java)

## Options

| Type     | Name            | Description                                                                                                                                                         | Example                                          |
|----------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| `String` | providerName    | Provider name to add. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`.                                             | `"azapi"`                                        |
| `String` | providerSource  | *Optional*. Provider source. When omitted, recipe adds `name = "version"`; when set, adds multiline object form with `source` and `version`. Supports placeholders. | `"azure/azapi"`                                  |
| `String` | providerVersion | Version constraint to add. Required and cannot be blank. Supports placeholders.                                                                                     | `"~> 2.5.0"`                                     |
| `String` | configuration   | *Optional*. Provider block body to append as `provider "name" { ... }` when such block does not already exist. Supports placeholders.                               | `"features {}"`                                  |
| `String` | source          | *Optional*. Module source filter; recipe applies only in files containing a matching module block. Supports placeholders.                                           | `"Azure/avm-res-network-privatednszone/azurerm"` |
| `String` | version         | *Optional*. Module version filter; recipe applies only in files containing a matching module block. Supports placeholders.                                          | `"0.3.5"`                                        |
| `String` | moduleName      | *Optional*. Module name filter; recipe applies only in files containing a matching module block. Supports placeholders.                                             | `"private_dns_zone"`                             |
| `String` | filePattern     | *Optional*. A glob pattern to match files to apply this recipe to.                                                                                                  | `"**/terraform.tf"`                              |

## Example

Based on migration changes where modules moving to `azapi` require explicit provider declaration.

**Before**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "0.3.5"
}

terraform {
  required_providers {
    azurerm = "~> 4.45.1"
  }
}
```

**After**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "0.3.5"
}

terraform {
  required_providers {
    azurerm = "~> 4.45.1"
    azapi = {
      source  = "azure/azapi"
      version = "~> 2.5.0"
    }
  }
}
```

## Usage

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.AddAzapiProvider
recipeList:
  - io.oczadly.openrewrite.hcl.AddProvider:
      providerName: "azapi"
      providerSource: "azure/azapi"
      providerVersion: "~> 2.5.0"
      moduleName: "private_dns_zone"
      source: "Azure/avm-res-network-privatednszone/azurerm"
      version: "0.3.5"
      filePattern: "**/terraform.tf"
```

Placeholders use `${property}` and `${property:default}`. For literal Terraform interpolation in generated values, use `${{...}}` in recipe config.
