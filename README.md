# Artifact Site Generator

A Java CLI that parses release artifacts and generates a fully static artifact registry site.

## Goals

- Parse local or remote artifacts through plugins.
- Persist normalized metadata in a local catalog.
- Generate a static website that looks similar to the Eclipse OpenVSIX registry experience.
- Keep the output hostable from any static file host (GitHub Pages, S3 static hosting, Nginx, Apache, etc.).

## Technology Choices

### Core Runtime

- **Java 17**
- **Maven** (multi-module build)
- **picocli** for CLI command parsing
- **PF4J** for parser plugin discovery and loading
- **Apache HttpClient 5** for remote artifact download/streaming
- **Jackson** (`jackson-databind`, `jackson-dataformat-yaml`) for metadata and config serialization
- **Apache FreeMarker** for static HTML templating
- **SLF4J + Logback** for logging
- **Apache Tika**: For mime-type and file type detection

### Testing

- **JUnit 5**
- **AssertJ**
- **Mockito**
- **Maven Surefire + Failsafe** for unit/integration test separation

## Repository / Module Plan

Create a Maven multi-module project:

- `artifact-site-parent` (root pom)
- `artifact-site-plugins-api`
  - Shared plugin interfaces and metadata model contracts
- `artifact-site-cli`
  - Main executable CLI, plugin loading, parse pipeline, static site generation
- `artifact-site-plugin-vsix`
  - VSIX parser plugin
- `artifact-site-plugin-maven`
  - Maven/JAR parser plugin
- `artifact-site-plugin-nifi-nar`
  - Apache NiFi NAR parser plugin

Build the CLI as an uberjar using `maven-shade-plugin`.

## XDG Paths (Concrete Locations)

Default paths must follow XDG with explicit fallbacks:

- **Config file**
  - `${XDG_CONFIG_HOME:-~/.config}/artifact-site-generator/config.yaml`
- **Plugin directory**
  - `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/plugins`
- **Metadata catalog directory**
  - `${XDG_STATE_HOME:-~/.local/state}/artifact-site-generator/catalog`
- **Working cache directory**
  - `${XDG_CACHE_HOME:-~/.cache}/artifact-site-generator`
- **Generated static site output**
  - `./public` by default (override with `--output`)

## Data Model

Each parsed artifact record should include:

- `id` (stable unique id, e.g., hash or `<group>:<artifact>:<version>`)
- `artifactName` (human-readable project name) **required**
- `artifactId` **required**
- `groupId` **required**
- `version` **required**
- `description`
- `authors` (list)
- `tags` (list)
- `license`
- `createdAt` (parse timestamp)
- `sourceType` (`local` or `remote`)
- `sourceValue` (local path or URL)
- `downloadUrl` (final URL used by the site download button)
- `fileName`
- `fileSizeBytes`
- `sha256`
- `pluginId` (which parser produced this record)
- `remoteHeaders` (optional list, present for remote sources)
- `remoteTls` (optional object, present for remote sources)
  - `insecureSkipVerify` (boolean)
  - `trustStorePath` (nullable string path)
  - `clientCertificatePath` (nullable string path)
  - `clientPrivateKeyPath` (nullable string path)

Catalog storage format:

- `catalog/artifacts.json` (canonical list used by generator)
- Optional split files for scale:
  - `catalog/artifacts/<artifact-id>/<version>.json`

## Plugin API Plan

Define in `artifact-site-plugins-api`:

- `ArtifactParserPlugin` (PF4J extension point)
- `ArtifactParser` interface:
  - `supports(ArtifactInputDescriptor descriptor): boolean`
  - `parse(ArtifactInputDescriptor descriptor, InputStream input, ArtifactParseContext context): ArtifactMetadata`
- `ArtifactInputDescriptor`:
  - input type, file name, extension, content type hints
- `ArtifactParseContext`:
  - checksum utilities
  - safe temp workspace
  - metadata writer helpers

Plugins decide artifact compatibility and produce normalized metadata only.

## CLI Command Plan

### `add-plugin`

```sh
artifact-site-generator add-plugin /path/to/plugin.jar
```

- Copies JAR into `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/plugins`.
- If the destination file already exists, it is replaced.
- Use `--plugin-dir /path/to/plugins` to install into a custom plugin directory.
- Validates readable JAR and deduplicates by filename/hash.

### `parse`

Local file:

```sh
artifact-site-generator parse /path/to/example.vsix
```

Remote URL:

```sh
artifact-site-generator parse https://example.com/releases/example.vsix
```

Behavior:

- Detect parser plugin via `supports`.
- Loads parser plugins from the default XDG plugin directory and, when provided, `--plugin-dir`.
- Parse metadata and append/update catalog.
- Resolve `downloadUrl`:
  - If input is **remote URL**, `downloadUrl` must be that exact URL.
  - If input is **local file**, copy artifact to:
    - `./public/downloads/<artifactId>/<version>/<fileName>`
    - and set `downloadUrl` to relative path:
      - `/downloads/<artifactId>/<version>/<fileName>`

REMOTE implementation notes (next milestone):

- Parse command should accept either a filesystem path or URL as the first argument.
- For remote input (`ArtifactSourceType.REMOTE`), download using Apache HttpClient 5 before parser invocation.
- Add repeatable header flags like `--http-header "Name=Value"` and attach them to the request.
- Add TLS flags:
  - `--remote-tls-trust-store /path/to/truststore-or-ca`
  - `--remote-tls-trust-store-password secret`
  - `--remote-tls-client-cert /path/to/client-cert`
  - `--remote-tls-client-key /path/to/client-key`
  - `--remote-tls-client-key-password secret`
- Persist the effective remote request metadata (`remoteHeaders` and `remoteTls`) with each parsed artifact record.

### `generate`

```sh
artifact-site-generator generate --output ./public
```

- Reads catalog and renders static files.
- Produces all pages, assets, and search index.
- Never requires a runtime backend service.
- Generated pages automatically support light/dark mode via system preference.
- Optional banner can be added globally:

```sh
artifact-site-generator generate \
  --bannerText="This application is in beta" \
  --bannerTextColorDark="black" \
  --bannerBackgroundColorDark="white" \
  --bannerTextColorLight="black" \
  --bannerBackgroundColorLight="white"
```

## Static Site Construction Plan

Use FreeMarker templates in `artifact-site-cli/src/main/resources/templates`:

- `layout.ftl` (base shell)
- `index.ftl` (registry listing page)
- `artifact.ftl` (artifact detail page)
- `tag.ftl` (tag filtered listing page)

Generated output in `./public`:

- `index.html`: Table lists unique parsers.
- `artifacts/<parser-type>/index.html`: Table lists unique `<group-id>.<artifact-id>`, pulls info from latest version.
- `artifacts/<parser-type>/<group-id>.<artifact-id>/index.html`: Table lists all artifact's versions, sorts by latest.
- `artifacts/<parser-type>/<group-id>.<artifact-id>/<version>/index.html`
- `tags/<tag>/index.html`
- `assets/styles.css`
- `assets/app.js`
- `assets/logo.svg`
- `search-index.json`
- `downloads/...` (for local artifact inputs only)

## UI/UX Direction (OpenVSIX-Inspired)

Match the OpenVSIX registry feel with:

- Top navigation with logo + search input
- Grid/list cards showing name, version, tags, and short description
- Artifact detail page with:
  - title, metadata table, version, authors, tags
  - clear **Download** button using `downloadUrl`
- Clean spacing, neutral light theme, subtle card borders/shadows
- Responsive layout for desktop/tablet/mobile

Implementation detail:

- Use server-free client-side filtering via `search-index.json` and vanilla JS.
- Keep CSS in a single static stylesheet; no runtime CSS framework required.

## End-to-End Flow

1. User installs parser plugins with `add-plugin`.
2. User runs `parse` for any number of local files or remote URLs.
3. CLI updates catalog metadata and copies local artifacts into `./public/downloads/...`.
4. User runs `generate`.
5. Static site appears in `./public` and can be hosted as-is.

## Non-Goals

- No database
- No server-side rendering at request time
- No authentication/authorization layer in this project
- No artifact upload API

## Delivery Milestones

- [x] **Bootstrap**
  - Multi-module Maven setup, CLI entrypoint, plugin loading.
- [x] **Plugin API**
  - Contracts, validation, metadata schema.
- [x] **Initial Plugin**
  - VSIX
- [x] **Catalog Persistence**
  - JSON catalog read/write/update rules.
- [x] **Break up ArtifactSiteGeneratorCli.java**
  - Moved CLI subcommands into focused command classes under `artifact-site-cli/src/main/java/com/nf3t/artifactsite/cli/`.
- [x] **Implement ArtifactSourceType.REMOTE**
  - Use Apache HttpClient 5 for download support.
  - Support CLI-provided TLS settings and HTTP headers.
  - Persist remote request configuration with artifact metadata. But seperate from artifacts.json (because artifacts.json will be used by static site generator)
- [x] **Static Generator**
  - FreeMarker templates + output assets.
- [x] **OpenVSIX-style UI**
  - Listing/detail pages + client-side search/filter.
- [ ] **More Plugins**
  - [x] VSIX
  - VintageStory Mod
  - Chrome Browser Extension
    - [x] Java JAR / Maven
    - NiFi NAR
- [ ] Fix search
- [ ] **Hardening**
  - Input validation, tests, docs, packaging, release steps.


## TODO

- Add java modules to src code?
