# FAQ

## What is the goal of this repository?

This repository provides small, production-ready [OpenRewrite](https://docs.openrewrite.org/) recipes for deterministic refactoring of [OpenTofu](https://opentofu.org/)/[Terraform](https://developer.hashicorp.com/terraform) (HCL) codebases.

The primary goal is to:

* safely migrate module usages between versions
* apply repetitive refactorings across many repositories
* deliver infrastructure changes as reviewable pull requests, not ad-hoc scripts

## How is this different from the official [OpenRewrite Terraform recipes](https://docs.openrewrite.org/recipes/terraform)?

The official [OpenRewrite Terraform recipes](https://docs.openrewrite.org/recipes/terraform) and [HCL recipes](https://docs.openrewrite.org/recipes/hcl) focus on generic syntax-level transformations.

This repository focuses on:

* module-aware refactoring (module name, source, version)
* migration-oriented use cases (breaking changes)
* idempotent, PR-driven workflows
* real-world infrastructure migrations (AVM, EKS, VPC)

Think of this as a higher-level, domain-specific layer built on top of [OpenRewrite](https://docs.openrewrite.org/).

## Does this replace [OpenTofu](https://opentofu.org/)/[Terraform](https://developer.hashicorp.com/terraform) upgrade tools?

No.

These recipes:

* do not inspect [OpenTofu state file](https://opentofu.org/docs/language/state/)
* do not apply infrastructure changes
* do not replace `init`, `plan`, or `apply`

They operate only on source code, before runtime.

## Why are only simple input types supported?

Currently, `AddModuleInput` supports string values only.

This is intentional.

Most real-world module migrations require:

* adding or removing string-based arguments
* introducing IDs, names, references, or paths
* refactoring configuration glue, not complex data structures

Support for additional types (`bool`, `number`, `list`, `map`) will be added incrementally, based on real migration needs.

## Are these recipes safe to run on large codebases?

Yes, when used as intended.

All recipes are designed to be:

* idempotent (re-running produces no additional changes)
* no-op if the target element does not exist
* compatible with rewriteDryRun before applying changes

The recommended workflow is:

1.	Run `./gradlew rewriteDryRun`
2.	Review changes
3.	Run `./gradlew rewriteRun`
4.	Commit as a PR

## Can I use these recipes in CI/CD pipelines?

Yes, but they are primarily designed for controlled refactoring workflows, not continuous enforcement.

Typical usage:

* migration PRs
* one-off or scheduled refactoring runs
* organization-wide upgrades

They are not intended as linting or policy enforcement rules.

## Are these recipes specific to [Azure](https://azure.microsoft.com/)/[AVM](https://azure.github.io/Azure-Verified-Modules/)?

No.

[AVM](https://azure.github.io/Azure-Verified-Modules/) is used as a realistic reference implementation, but the recipes themselves are:

* cloud-agnostic
* registry-agnostic
* compatible with any [OpenTofu](https://opentofu.org/)/[Terraform](https://developer.hashicorp.com/terraform) module layout

The same approach applies equally to:

* AWS modules (EKS, VPC)
* internal organization modules
* local modules

## Why YAML-based composite recipes?

YAML recipes:

* separate migration knowledge from implementation
* allow non-Java users to define migrations
* are easy to review, version, and reuse

Java recipes provide the engine; YAML recipes define what to change.

## Is this project production-ready?

Yes, within its defined scope.

The recipes:

* are unit-tested
* designed for idempotence
* already used in real refactoring scenarios

However, this is not a general-purpose [OpenTofu](https://opentofu.org/)/[Terraform](https://developer.hashicorp.com/terraform) refactoring engine and does not aim to be one.

## Is this project affiliated with [Moderne](https://www.moderne.ai/) or [OpenRewrite](https://docs.openrewrite.org/)?

No.

This is an independent project built on top of [OpenRewrite](https://docs.openrewrite.org/), inspired by its design principles and documentation style.

## Can I contribute?

See [CONTRIBUTING.md](../CONTRIBUTING.md) or open an issue to discuss your proposal.

## What is explicitly out of scope?

* [OpenTofu state file](https://opentofu.org/docs/language/state/) manipulation
* Runtime-aware refactoring
* Opinionated formatting or style enforcement
* Policy enforcement / compliance checks

## How stable is the API?

The public recipe API is intended to be stable.

Breaking changes will follow semantic versioning and be documented in release notes.
