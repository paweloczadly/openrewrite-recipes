# AGENTS.md

## Project snapshot
- This is a single-module Java library of OpenRewrite recipes for HCL/OpenTofu modules; production code is under `src/main/java/io/oczadly/openrewrite/hcl`.
- Core abstraction: every recipe extends `ModuleRecipe` (`src/main/java/io/oczadly/openrewrite/hcl/ModuleRecipe.java`), which enforces file filtering and shared module matching.
- Current recipe set: `AddModuleInput`, `ChangeModuleVersion`, `RemoveModuleInput` (same package).
- Utility matching/parsing helpers live in `src/main/java/io/oczadly/openrewrite/hcl/utils/ModuleBlockPredicates.java`.

## How recipe execution is structured
- `ModuleRecipe.getVisitor()` wraps recipe visitors with `Preconditions.check(new FindSourceFiles(filePatternOrDefault), ...)`; default pattern is `**/*.tf`.
- Recipe visitors operate at `Hcl.Block` level and call `matchesModule(block)` before mutating.
- Matching semantics are strict: `source` is required and compared exactly, `moduleName` and `version` are optional filters.
- `ModuleBlockPredicates.getAttributeValue()` strips quotes before comparisons, so recipe matching should use unquoted values.
- After mutations, recipes schedule `SpacesVisitor` to normalize HCL formatting (see all three recipe classes).

## Local workflows that mirror CI/CD
- CI (`.github/workflows/ci.yml`) runs commitlint plus `./gradlew build` on Java 17.
- Release pipeline (`.github/workflows/cd.yml`) runs semantic-release (Node 20), then publishes with `./gradlew publishAllPublicationsToMavenCentralRepository --no-configuration-cache` on Java 21.
- Project version is derived from `RELEASE_VERSION` env var or defaults to `0.0.1-SNAPSHOT` (`build.gradle.kts`).
- Use Gradle wrapper commands from repo root:
  - `./gradlew build`
  - `./gradlew test`
  - `./gradlew test --tests '*AddModuleInputTest'`

## Code conventions to follow in this repo
- Aim for Staff Engineer-level quality: clear design, safe changes, and maintainable implementation quality.
- Keep solutions clean and pragmatic; avoid over-engineering and unnecessary abstractions.
- When using OpenRewrite APIs, verify behavior against OpenRewrite source/docs for the exact versions pinned in `gradle/libs.versions.toml` (especially `openrewrite` and `openrewrite-bom`).
- Constructor argument order is consistent: shared module filters first (`moduleName`, `source`, `version`), then recipe-specific options, then `filePattern`.
- Public recipe options are declared with `@Option`; validation happens in `validate()` and is heavily assertion-tested for exact failure messages.
- Keep `source` mandatory in new `ModuleRecipe` subclasses; tests expect invalid state when `source` is null/blank (`ModuleRecipeTest`).
- If you add/remove HCL attributes manually, preserve existing indentation strategy (`detectIndentation`) and then run `SpacesVisitor`.
- Follow Lombok style used here (`@Value`, `@EqualsAndHashCode(callSuper = false)`) unless a class needs mutability.

## Testing patterns and examples
- Tests use OpenRewrite `RewriteTest` DSL with `rewriteRun(...)` and `hcl(before, after)` assertions.
- Set a default recipe in `defaults(RecipeSpec spec)` for each test class, then override per test when needed.
- Path-scoped behavior is tested via `spec.path("...")`; see `AddModuleInputTest` and `RemoveModuleInputTest` for glob/filePattern expectations.
- Validation tests use `@CsvSource` and assert both `failures().hasSize(1)` and exact message strings.
- Include at least one `@DocumentExample` test when adding a new recipe behavior.

## Integration and release-aware edits
- If recipe API/options change, update matching docs in `docs/recipes/*.md` (release automation edits version strings there via `.releaserc`).
- Changelog and docs are commit assets in semantic-release (`.releaserc`), so keep docs consistent with code changes.
- Publishing/signing are configured via Gradle properties and CI secrets; do not hardcode credentials.
- Treat `build/` outputs as generated artifacts; edit source under `src/` and `docs/` instead.
