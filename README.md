# golemcore-plugins

`golemcore-plugins` is the external plugin repository for the GolemCore runtime.
It contains the shared Java contracts that plugins compile against, the first-party plugin modules shipped by the GolemCore team, and the registry metadata that describes every released plugin artifact.

This repository is designed for two workflows:

- building and validating plugin modules inside one Maven reactor
- releasing versioned plugin artifacts and registry metadata from the default branch through CI

## What Lives Here

```text
.
|-- extension-api/        # Extension-facing SPI and shared DTO contracts
|-- runtime-api/          # Runtime-facing interfaces provided by the engine
|-- golemcore/*           # First-party plugin modules
|-- registry/             # Marketplace metadata for released plugin versions
|-- dist/                 # Locally built plugin artifacts
|-- scripts/              # Repository validation and release helpers
|-- CONTRIBUTING.md       # Contribution, versioning, and release rules
`-- docs/CODING_GUIDE.md  # Repository-wide coding conventions
```

### Shared APIs

The repository publishes two shared libraries used by plugin modules:

- `me.golemcore.plugins:golemcore-plugin-extension-api`
  Extension-facing SPI, tool/provider contracts, and shared execution DTOs.
- `me.golemcore.plugins:golemcore-plugin-runtime-api`
  Engine-provided runtime interfaces and DTOs that plugins can depend on without importing engine internals.

These libraries are versioned at the repository level and are intentionally separate from per-plugin release numbers.

## First-Party Plugins

The current `golemcore/*` modules in this repository are:

| Plugin | Purpose |
| --- | --- |
| `golemcore/brave-search` | Brave Search web search tool plugin with API-key backed queries. |
| `golemcore/browser` | Playwright-backed browser automation tool with screenshot support. |
| `golemcore/browserless` | Browserless smart scrape plugin for rendered HTML, markdown, and link extraction. |
| `golemcore/elevenlabs` | ElevenLabs-backed STT/TTS provider plugin. |
| `golemcore/firecrawl` | Firecrawl-backed page scraping plugin for markdown, summary, HTML, and link extraction. |
| `golemcore/lightrag` | LightRAG-backed retrieval provider plugin for prompt augmentation and indexing. |
| `golemcore/mail` | IMAP read and SMTP send tool plugin for mailbox integrations. |
| `golemcore/notion` | Notion vault plugin backed by the official Notion HTTP API. |
| `golemcore/obsidian` | Obsidian vault plugin backed by obsidian-local-rest-api. |
| `golemcore/perplexity-sonar` | Perplexity Sonar grounded-answer plugin with configurable model selection and synchronous completions. |
| `golemcore/pinchtab` | PinchTab browser automation plugin for navigation, snapshots, actions, text extraction, and screenshots. |
| `golemcore/tavily-search` | Tavily-backed web search tool plugin with configurable search depth and answer generation. |
| `golemcore/telegram` | Telegram channel, invite onboarding, confirmations, and plan approval integration. |
| `golemcore/slack` | Slack Socket Mode channel plugin with thread follow-ups, confirmations, and plan approval UI. |
| `golemcore/supabase` | Supabase PostgREST rows plugin with configurable read/write operations for database tables. |
| `golemcore/weather` | Open-Meteo weather tool plugin with no external credentials required. |
| `golemcore/whisper` | Whisper-compatible speech-to-text provider plugin. |

Each plugin module contains its own Maven project, `plugin.yaml` descriptor, tests, and packaging configuration.

## Plugin Packaging Model

Every plugin release is represented by three sources of truth that must stay aligned:

1. the plugin module version in `golemcore/<plugin>/pom.xml`
2. the plugin manifest in `golemcore/<plugin>/plugin.yaml`
3. the released metadata in `registry/<owner>/<plugin>/`

Plugin artifacts are packaged as shaded JARs and copied into `dist/<owner>/<plugin>/<version>/` during local builds.
Released registry metadata records the artifact path, checksum, publish timestamp, and source commit for that version.

Released versions are immutable.
If plugin code changes after a version has been released, ship a new SemVer version instead of rewriting the checksum or metadata of an existing one.

## Build And Validate

### Full repository verification

Use the strict Maven profile for repository-level verification:

```bash
mvn -B -ntp -P strict verify
```

This runs the formatter, unit tests, PMD, SpotBugs, and plugin packaging checks across the reactor.

### Repository metadata validation

Use the helper script to validate manifests, module versions, and registry metadata:

```bash
python3 scripts/plugins_repo.py validate
```

## Release Model

Normal plugin releases are produced from the default branch.
The release pipeline determines which plugins changed, derives the SemVer bump from conventional commits, packages the plugin artifact, refreshes `registry/` metadata, publishes the artifact, and creates the release tag.

Release tag format:

```text
<owner>-<plugin>-v<version>
```

Examples:

- `golemcore-telegram-v1.2.0`
- `golemcore-whisper-v2.0.1`

## Commit And PR Conventions

This repository uses conventional commits for both commit subjects and PR titles.
That matters because squash-merge commit titles are part of the release signal on `main`.

Common examples:

- `feat(golemcore/telegram): add callback retry backoff`
- `fix(golemcore/whisper): reject empty STT responses`
- `docs(repo): add repository overview`
- `build(repo): align plugin packaging with shaded jars`

Breaking changes must use `!` or a `BREAKING CHANGE:` footer.

## CI At A Glance

The main pipelines are:

- `Semantic PR Title`
- `Conventional Commits`
- `CI`
- `CI / release_main`
- `Release Plugin`

Together they validate repository structure, plugin metadata alignment, formatting, strict Maven checks, and the automated release flow.

## Contributing

Start with:

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [docs/CODING_GUIDE.md](docs/CODING_GUIDE.md)

`CONTRIBUTING.md` is the source of truth for versioning rules, release semantics, merge policy, and CI expectations.
