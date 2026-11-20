# Change provider version

`io.oczadly.openrewrite.hcl.ChangeProviderVersion`

Changes the version of a provider in `terraform { required_providers { } }`.

## Recipe source

[ChangeProviderVersion.java](../../src/main/java/io/oczadly/openrewrite/hcl/ChangeProviderVersion.java)

## Options

| Type     | Name            | Description                                                                  | Example            |
|----------|-----------------|------------------------------------------------------------------------------|--------------------|
| `String` | providerSource  | The source of the provider to change. It must match exactly.                 | `"hashicorp/aws"`  |
| `String` | newVersion      | The new version. It is written as you give it.                               | `"~> 6.0"`         |
| `String` | providerName    | *Optional*. Change only the provider with this name in `required_providers`. | `"google-beta"`    |
| `String` | providerVersion | *Optional*. Change the provider only if its current version matches this.    | `"~> 5.0"`         |
| `String` | filePattern     | *Optional*. Change only the files that match this pattern.                   | `"**/versions.tf"` |

You can use placeholders like `${property}` in every option except `filePattern`.

## Version filter semantics

When `providerVersion` is specified, it is a semantic version constraint, as the `version` option of [Change module version](ChangeModuleVersion.md). It supports `=`, `!=`, `>`, `>=`, `<`, `<=`, `~>`, and comma-separated AND constraints such as `>= 5.0, < 6.0`. For example, `~> 5.0` matches a current version of `5.82.1` or `~> 5.82`.

The current version is matched by its number only:

* Its operator is ignored, so `< 5.0` is compared as `5.0` and matches `~> 5.0`.
* A current version with `!=` or with more than one clause, such as `>= 5.0, < 6.0`, never matches.
* A missing, empty, or dynamic current version never matches.

Providers use ranges more often than modules, so check the current versions before you set `providerVersion`. Without `providerVersion`, every matching provider is changed, whatever its current version.

## What it changes

The recipe changes the version of a provider in the `required_providers` block of a top-level `terraform` block. It works with both forms:

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    helm = "~> 2.17"
  }
}
```

When an entry has no `source`, such as the short entry `helm = "~> 2.17"`, Terraform reads it as `hashicorp/helm`, so it matches `providerSource: "hashicorp/helm"`.

The recipe does not change:

* `provider` blocks and `module` blocks.
* A provider without a version.
* A version that is not a plain string, such as `var.aws_version` or `"~> ${var.major}.0"`.
* A provider entry with quoted keys, such as `"version" = "~> 5.0"`.

## Example

| Parameter       | Value             |
|-----------------|-------------------|
| providerSource  | `"hashicorp/aws"` |
| providerVersion | `"~> 5.0"`        |
| newVersion      | `"~> 6.0"`        |

**Before**

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
```

**After**

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.providers.ChangeAwsProviderFrom5xTo6x`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

```hcl title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.providers.ChangeAwsProviderFrom5xTo6x
displayName: hashicorp/aws 5.x -> 6.x
description: Update the hashicorp/aws provider constraint from 5.x to 6.x
recipeList:
  - io.oczadly.openrewrite.hcl.ChangeProviderVersion:
      providerSource: "hashicorp/aws"
      providerVersion: "~> 5.0"
      newVersion: "~> 6.0"
```

Now that `io.oczadly.providers.ChangeAwsProviderFrom5xTo6x` has been defined, activate it in your build file:

1. Add the following to your **build.gradle.kts** file:

```kotlin title="build.gradle.kts"
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("io.oczadly:openrewrite-recipes:1.7.1")
}

rewrite {
    activeRecipe("io.oczadly.providers.ChangeAwsProviderFrom5xTo6x")
}
```

2. Run `gradle rewriteRun` to run the recipe.
