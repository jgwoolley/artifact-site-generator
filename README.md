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
- **SLF4J + slf4j-simple** for logging

### Testing

- **JUnit 5**
- **AssertJ**
- **Maven Surefire + Failsafe** for unit/integration test separation

## Repository / Module Plan

The Maven multi-module project contains:

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

Plugin modules may use libraries already supplied by the CLI as `provided` dependencies. This keeps plugin JARs lightweight and ensures the CLI owns the shared runtime version.

## XDG Paths (Concrete Locations)

The CLI currently uses `XDG_DATA_HOME` with an explicit fallback:

- **Plugin directory**
  - `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/plugins`
- **Artifact catalog**
  - `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/artifacts.json`
- **Remote download cache**
  - `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-cache`
- **Remote request configuration**
  - `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-requests.json`
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
- Remote request headers and TLS settings are stored separately in
  `remote-requests.json`, keyed by artifact ID.

Catalog storage format:

- `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/artifacts.json`
- `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-requests.json`

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
- Parse metadata and append/update the catalog. Equal records are identified by
  their stable artifact ID; when the same record is read again, the later
  metadata replaces the earlier record while other versions are retained.
- Resolve `downloadUrl`:
  - If input is **remote URL**, `downloadUrl` must be that exact URL.
  - If input is **local file**, the generator copies it to
    `./public/downloads/<artifactId>/<version>/<fileName>` and uses the
    corresponding relative path.

- Remote inputs are downloaded with Apache HttpClient 5 before parser invocation.
- Repeatable `--http-header "Name=Value"` options are supported.
- Trust-store and client-certificate TLS options are supported.
- Effective remote request metadata is persisted separately in
  `remote-requests.json`.

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

Artifact parser metadata sources:

- Maven reads `name`, `description`, `developers`, and `scm` from the
  embedded `META-INF/maven/<group>/<artifact>/pom.xml`.
- VSIX reads `displayName`, `description`, `author`, `contributors`, and
  `repository` from the packaged `package.json`, with manifest fallbacks for
  display name, description, and source URL.

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
3. CLI updates catalog metadata.
4. User runs `generate`.
5. The generator copies local artifacts into `./public/downloads/...` and the
   static site appears in `./public` and can be hosted as-is.

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
  - Listing/detail pages
- [x] **Client-side search/filter**
- [ ] **More Plugins**
  - [x] VSIX
  - VintageStory Mod
  - Chrome Browser Extension
  - [x] Java JAR / Maven
  - NiFi NAR
- [ ] **Hardening**
  - Input validation, tests, docs, packaging, release steps.

## TODO

- Add java modules to src code?
- More sensible commands like "artifact add" "plugin add" "artifact clear" "plugin clear"s
- Add url to background href / a element.
- Override FTL files plugin behavior?
- ArtifactParseContext is strange in what is stored there.
- Parsers should avoid reading in all the bytes to memory.
- SHA-256 should include a download link
- There should be download instructions that are specific for each processor.
- Add a clear that clears all user settings / data command