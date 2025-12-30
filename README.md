# openrewrite-recipes

[![Latest Release](https://img.shields.io/github/v/release/paweloczadly/openrewrite-recipes?label=release)](https://github.com/paweloczadly/openrewrite-recipes/releases/latest)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.oczadly/openrewrite-recipes)](https://central.sonatype.com/artifact/io.oczadly/openrewrite-recipes/overview)

A collection of [OpenRewrite](https://docs.openrewrite.org/) recipes for automated refactoring.

## Table of contents

* [Usage](#usage)
* [Recipes catalog](#recipes-catalog)
    * [HCL recipes](#hcl-recipes)
* [Contributing](#contributing)
* [FAQ](#faq)
* [Roadmap](#roadmap)
* [License](#license)
* [Author](#author)

## Usage

To integrate these recipes into your OpenRewrite automation:

1. **Add the dependency** to your OpenRewrite Maven/Gradle configuration.
2. **Configure recipes** in your refactoring rules.
3. **Run** OpenRewrite to apply transformations.

For detailed instructions, examples, and advanced configuration, see the recipe documentation (e.g., [Add module input](docs/recipes/AddModuleInput.md)).

---

## Recipes catalog

This section contains all available recipes organized by module type.

### HCL recipes

These recipes enable automated modifications to [OpenTofu](https://opentofu.org/) configurations. The recipes handle common operations on [OpenTofu](https://opentofu.org/) module blocks, allowing for programmatic updates across your infrastructure-as-code codebase.

> [!NOTE]
> The main focus is [OpenTofu](https://opentofu.org/), but these recipes should work with [Terraform](https://developer.hashicorp.com/terraform) too.
**Available recipes**

| Name                                                         | Description                                                       |
|--------------------------------------------------------------|-------------------------------------------------------------------|
| [Add module input](docs/recipes/AddModuleInput.md)           | Adds a specified input variable to a Terraform module block.      |
| [Change module version](docs/recipes/ChangeModuleVersion.md) | Changes the version of a Terraform module.                        |
| [Remove module input](docs/recipes/RemoveModuleInput.md)     | Removes a specified input variable from a Terraform module block. |

---

## Contributing

> [!IMPORTANT]
> Please note that these recipes are developed and maintained in focused time blocks to ensure quality. Contributions and issues will be addressed on a best-effort basis, depending on ongoing priorities.
Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## FAQ

See [FAQ.md](docs/FAQ.md) for answers to common questions.

---

## Roadmap

See [ROADMAP.md](docs/ROADMAP.md) for planned features and future developments.

---

## License

MIT License – see [LICENSE](LICENSE) for details.

---

## Author

Paweł Oczadły ([GitHub](https://github.com/paweloczadly) / [LinkedIn](https://linkedin.com/in/paweloczadly))
