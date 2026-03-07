# Contributing

## Coding Guide

Repository-wide coding conventions live in `docs/CODING_GUIDE.md`.
Follow them for `extension-api/`, `runtime-api/`, and `golemcore/*` modules unless a module documents stricter rules.

## Versioning

- Each plugin is versioned independently with SemVer.
- The source of truth for the current plugin version is the child module `pom.xml`.
- `plugin.yaml` and `registry/` metadata must match the module version.
- Current `registry/<owner>/<plugin>/versions/<current>.yaml` must point at the local `dist/...jar` artifact and its `checksumSha256` must match the built jar.
- Parent `pom.xml` is repository-level and is not used for plugin release numbering.
- `extension-api/` produces `me.golemcore.plugins:golemcore-plugin-extension-api`, the extension contract shared by all plugins in this repository.
- `runtime-api/` produces `me.golemcore.plugins:golemcore-plugin-runtime-api`, the isolated engine-provided runtime interface layer consumed by plugins.
- `extension-api/` must contain only extension-facing contracts: SPI, extension ports, and shared event/DTO types that are part of the plugin execution contract.
- `runtime-api/` must contain only runtime-facing interfaces and DTOs. Engine implementation classes and plugin-internal helpers must never live there.
- Plugin-internal helpers, validators, runtime behavior, and test support must stay inside the owning plugin module.
- Java formatting is mandatory for `extension-api/`, `runtime-api/`, and `golemcore/*` plugin modules. External contributor modules are not forced to use the formatter by default.

Release tag format:

- `<owner>-<plugin>-v<version>`

Examples:

- `golemcore-telegram-v1.2.0`
- `golemcore-whisper-v2.0.1`

## Conventional Commits

Use conventional commit titles for every PR and for commits that land on the default branch.

Supported types:

- `feat`
- `fix`
- `perf`
- `refactor`
- `build`
- `ci`
- `docs`
- `test`
- `chore`
- `revert`
- `release`

Release bump rules:

- `feat` -> `minor`
- `fix`, `perf`, `refactor`, `build`, `revert` -> `patch`
- `!` or `BREAKING CHANGE:` -> `major`
- `ci`, `docs`, `test`, `chore`, `release` do not trigger an automatic plugin version bump

Examples:

- `feat(golemcore/telegram): add callback retry backoff`
- `fix(golemcore/whisper): reject empty STT responses`
- `build(repo): align plugin packaging with shaded jars`

Breaking changes must use `!` or a `BREAKING CHANGE:` footer.

Examples:

- `feat(golemcore/telegram)!: replace invite payload format`
- `refactor(golemcore/elevenlabs): rename provider ids`
  `BREAKING CHANGE: old provider aliases were removed`

## Merge Policy

- Prefer squash merge.
- The PR title must already be a valid conventional commit because the squash commit becomes the release signal on `main`.

## CI

The repository pipelines are:

- `Semantic PR Title`
  - enforces a conventional PR title for squash merges
- `Conventional Commits`
  - validates commit subjects in PRs and on `main`
- `CI`
  - validates repository structure and builds `extension-api/`, `runtime-api/`, and all plugins
- `Release Plugin`
  - bumps, packages, tags, and publishes one plugin release

The `CI` pipeline validates:

- repository structure and registry consistency
- `golemcore-plugin-extension-api` compile-time contract availability
- `golemcore-plugin-runtime-api` runtime boundary availability
- mandatory formatter wiring for `extension-api/`, `runtime-api/`, and `golemcore/*`
- plugin manifest and module version alignment
- registry checksum consistency against locally built `dist/` artifacts
- Maven build/tests plus mandatory PMD and SpotBugs checks (`-P strict verify`)
- plugin package and shaded artifact generation

CI builds `golemcore-plugin-extension-api` inside the same reactor, so the plugins repository no longer needs to check out or install `golemcore-bot` during verification.

## Releases

Plugin releases are produced with the manual GitHub Actions workflow `Release Plugin`.

The workflow:

1. validates the repository
2. bumps one plugin version
3. packages that plugin
4. refreshes `registry/` metadata with checksum and published timestamp
5. commits the release metadata
6. tags the release
7. publishes the jar and checksum file to GitHub Releases

For local marketplace development after rebuilding plugin jars without a version bump, refresh registry metadata with:

- `python3 scripts/plugins_repo.py sync-local-registry`

Then verify the result with:

- `python3 scripts/plugins_repo.py validate --check-local-artifacts`

For normal releases use `bump=auto`. It derives `major` / `minor` / `patch` from conventional commits since the last plugin tag.

Use `version_override` only for backfills or exceptional releases.
