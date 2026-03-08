# Contributing

## Coding Guide

Repository-wide coding conventions live in `docs/CODING_GUIDE.md`.
Follow them for `extension-api/`, `runtime-api/`, and `golemcore/*` modules unless a module documents stricter rules.

## Versioning

- Each plugin is versioned independently with SemVer.
- The source of truth for the current plugin version is the child module `pom.xml`.
- `plugin.yaml` and `registry/` metadata must match the module version.
- Shared repository APIs use the repository-level `plugin.api.version`. They do not automatically follow individual plugin release bumps.
- Released plugin versions are immutable. Never replace the jar, checksum, or metadata of an already released `registry/<owner>/<plugin>/versions/<released>.yaml`.
- Code changes to a released plugin must ship as a new SemVer version. Do not rewrite the checksum of an existing released version in a PR.
- The checksum recorded in `registry/<owner>/<plugin>/versions/<new-version>.yaml` is derived from the artifact built by the release workflow from the repository state on the default branch.
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
- `CI / release_main`
  - after a merge to the default branch, determines which plugins require a release, bumps versions, and publishes release artifacts
- `Release Plugin`
  - manually backfills or forces one plugin release from the default branch

The `CI` pipeline validates:

- repository structure and registry consistency
- `golemcore-plugin-extension-api` compile-time contract availability
- `golemcore-plugin-runtime-api` runtime boundary availability
- mandatory formatter wiring for `extension-api/`, `runtime-api/`, and `golemcore/*`
- plugin manifest and module version alignment
- Maven build/tests plus mandatory PMD and SpotBugs checks (`-P strict verify`)
- plugin package and shaded artifact generation

The normal PR/build pipeline does not compare locally built jars against checksums of already released versions in `registry/`. That checksum is release metadata, not a PR maintenance task.

CI builds `golemcore-plugin-extension-api` inside the same reactor, so the plugins repository no longer needs to check out or install `golemcore-bot` during verification.

## Releases

Normal plugin releases are produced automatically after a merge to the default branch. The `CI / release_main` job:

1. determines which plugins need a release from the merged commit range
2. derives the SemVer bump from conventional commits since the last plugin tag
3. bumps the plugin version
4. packages the plugin from the repository state on the default branch
5. writes fresh `registry/` metadata, including checksum and published timestamp, from that built artifact
6. publishes the jar to GitHub Packages
7. commits release metadata, tags the release, and publishes the jar plus checksum file to GitHub Releases

The manual GitHub Actions workflow `Release Plugin` exists for backfills or exceptional releases from the default branch. It follows the same packaging and checksum rules as the automatic release flow.

For local marketplace development after rebuilding plugin jars without a version bump, refresh registry metadata with:

- `python3 scripts/plugins_repo.py sync-local-registry`

Then verify the result with:

- `python3 scripts/plugins_repo.py validate --check-local-artifacts`

This local registry sync is only for local development or unreleased artifacts. Do not use it to rewrite the checksum of an already released version in a PR.

For normal releases use `bump=auto`. It derives `major` / `minor` / `patch` from conventional commits since the last plugin tag.

Use `version_override` only for backfills or exceptional releases.
