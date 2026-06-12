# Remove provider

`io.oczadly.openrewrite.hcl.RemoveProvider`

Removes entries from `terraform.required_providers` and optionally removes matching top-level `provider` blocks.

## Recipe source

[RemoveProvider.java](../../src/main/java/io/oczadly/openrewrite/hcl/RemoveProvider.java)

## Options

| Type      | Name                | Description                                                                                                                                    | Example                                          |
|-----------|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| `String`  | providerName        | Provider name to remove. Required and cannot be blank. Supports placeholders like `${property}` and `${property:default}`.                     | `"modtm"`                                        |
| `Boolean` | removeConfiguration | *Optional*. Remove matching top-level `provider "name" {}` blocks. Defaults to `true` when omitted.                                            | `false`                                          |
| `String`  | source              | *Optional*. Module source filter; recipe applies only in files containing a matching module block. Supports placeholders.                      | `"Azure/avm-res-network-privatednszone/azurerm"` |
| `String`  | version             | *Optional*. Module semantic version constraint filter; recipe applies only in files containing a matching module block. Supports placeholders. | `"~> 0.3.0"`                                     |
| `String`  | moduleName          | *Optional*. Module name filter; recipe applies only in files containing a matching module block. Supports placeholders.                        | `"private_dns_zone"`                             |
| `String`  | filePattern         | *Optional*. A glob pattern to match files to apply this recipe to.                                                                             | `"**/terraform.tf"`                              |

## Example

When `version` is specified, it is interpreted as a semantic version constraint. Matching module blocks may declare either a concrete stable version literal such as `0.3.5`, or a single operator-prefixed value such as `~> 0.3.5` where the operator is stripped and the numeric portion is matched.

**Before**

```hcl
module "private_dns_zone" {
  source  = "Azure/avm-res-network-privatednszone/azurerm"
  version = "0.3.5"
}

terraform {
  required_providers {
    modtm = {
      source  = "azure/modtm"
      version = "~> 0.3.5"
    }
    azurerm = "~> 4.45.1"
  }
}

provider "modtm" {
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
  }
}
```

## Usage

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.avm.migrations.RemoveModtmProvider
recipeList:
  - io.oczadly.openrewrite.hcl.RemoveProvider:
      providerName: "modtm"
      moduleName: "private_dns_zone"
      source: "Azure/avm-res-network-privatednszone/azurerm"
      version: "~> 0.3.0"
      filePattern: "**/terraform.tf"
```

Set `removeConfiguration: false` when you only want to delete the `required_providers` entry and keep `provider "name"` blocks unchanged.
