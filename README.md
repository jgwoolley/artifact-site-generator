# Artifact Site Generator

A Java CLI that parses release artifacts (VSIX extensions, Maven/JAR artifacts, and more via plugins) and generates a fully static, browsable artifact registry site inspired by the Eclipse OpenVSIX registry UI.

## How it works

1. Install parser plugins with `add-plugin`.
2. Parse local files or remote URLs with `parse`. Metadata is normalized and appended to a local JSON catalog.
3. Run `generate` to render the catalog into a static site (HTML, CSS, JS, search index) that can be hosted
   from any static file host (GitHub Pages, S3, Nginx, Apache, etc.) with no backend service required.

## Technology

- **Java 17**, **Maven** (multi-module build)
- **picocli** for CLI command parsing
- **PF4J** for parser plugin discovery and loading
- **Apache HttpClient 5** for remote artifact download/streaming
- **Jackson** for metadata and config serialization
- **Apache FreeMarker** for static HTML templating
- **commonmark-java** for rendering discovered READMEs to HTML
- **SLF4J + slf4j-simple** for logging
- **JUnit 5 + AssertJ**, with Maven Surefire/Failsafe for unit/integration test separation

## Modules

- `artifact-site-plugins-api` - shared plugin interfaces and metadata model contracts
- `artifact-site-cli` - main executable CLI: plugin loading, parse pipeline, static site generation
- `artifact-site-plugin-vsix` - VSIX parser plugin
- `artifact-site-plugin-maven` - Maven/JAR parser plugin

The CLI builds as an uberjar via `maven-shade-plugin`. Plugin modules depend on CLI-supplied libraries as `provided`, so the CLI owns the shared runtime version and plugin JARs stay lightweight.

## CLI commands

All commands accept the global `--plugin-dir <path>` and `--artifact-json <path>` options to override the default XDG-based locations below.

### `add-plugin`

```sh
artifact-site-generator add-plugin /path/to/plugin.jar
```

Copies the JAR into the plugin directory, replacing any existing file with the same name + version.

### `list-plugins`

Loads plugins from the plugin directory and prints each plugin's ID and registered extensions.

### `clear-plugins`

Deletes every JAR in the plugin directory.

### `parse`

```sh
artifact-site-generator parse /path/to/example.vsix
artifact-site-generator parse https://example.com/releases/example.vsix
```

- Accepts any number of local paths and/or remote URLs in one call.
- Loads parser plugins and picks the first one whose `supports(...)` matches the input.
- Remote inputs are downloaded via Apache HttpClient 5 before parsing. Supports repeatable
  `--http-header "Name=Value"` options and TLS options (`--remote-tls-trust-store`,
  `--remote-tls-trust-store-password`, `--remote-tls-client-cert`, `--remote-tls-client-key`,
  `--remote-tls-client-key-password`).
- Parsed metadata is appended/updated in the catalog: records are identified by a stable artifact ID, and
  re-parsing the same artifact replaces its record while other versions are retained.
- `downloadUrl` resolution: remote inputs keep their source URL; local files are copied into
  `./public/downloads/<artifactId>/<version>/<fileName>` during `generate`.
- Effective remote request metadata (headers, TLS settings) is persisted separately in
  `remote-requests.json`, keyed by artifact ID, so it never ends up in the catalog consumed by the site
  generator.

### `list-artifacts`

Loads plugins and the catalog, then prints every parsed artifact record.

### `info`

Prints the resolved plugin directory, catalog path, remote cache directory, and remote request config path.

### `generate`

```sh
artifact-site-generator generate --output ./public
```

Reads the catalog and renders the static site: all pages, assets, and the search index. Optionally set a
global banner shown on every page:

```sh
artifact-site-generator generate \
  --bannerText="This application is in beta" \
  --bannerTextColorDark="black" \
  --bannerBackgroundColorDark="white" \
  --bannerTextColorLight="black" \
  --bannerBackgroundColorLight="white"
```

Generated pages support light/dark mode via system preference.

## XDG paths

- Plugin directory: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/plugins`
- Artifact catalog: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/artifacts.json`
- Remote download cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-cache`
- Remote request config: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-requests.json`
- Parser icon cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/icons`
- Parser display name cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/parser-display-names.json`
- Generated static site: `./public` by default (override with `--output`)

## Data model

Each catalog record (`ArtifactMetadata`) includes:

- `id` (stable unique id, e.g., hash or `<group>:<artifact>:<version>`)
- `artifactName`, `artifactId`, `groupId`, `version` - required
- `description`, `authors` (list), `tags` (list), `license`
- `createdAt` (parse timestamp)
- `sourceType` (`local` or `remote`), `sourceValue` (local path or URL)
- `downloadUrl`, `fileName`, `fileSizeBytes`, `sha256`
- `pluginId` (which parser produced this record)
- `readme` (raw README markdown discovered inside the artifact, if any - see below)

Parser-specific metadata sources:

- **Maven**: reads `name`, `description`, `developers`, and `scm` from the embedded
  `META-INF/maven/<group>/<artifact>/pom.xml`.
- **VSIX**: reads `displayName`, `description`, `author`, `contributors`, and `repository` from the packaged
  `package.json`, with manifest fallbacks for display name, description, and source URL. Also discovers a
  packaged `readme.md` (case-insensitive; when more than one exists, the one closest to the extension's
  `extension/` root wins).

### README rendering

A parser can populate `ArtifactMetadata.readme` with the raw markdown text of a README it finds inside the
artifact (only the VSIX parser does this today, via the extension's packaged `readme.md`; the metadata field
itself is generic, so any parser can support it). `generate` renders that markdown to HTML with
commonmark-java and makes it the main content of the artifact detail page, with **raw HTML in the source
markdown escaped rather than passed through** - READMEs come from arbitrary third-party artifacts, so this
keeps a malicious one from injecting a `<script>` or other active content into the generated site. An artifact
with no README shows a plain "No README was found for this artifact." message instead.

## Plugin API

Defined in `artifact-site-plugins-api`:

- `ArtifactParserPlugin` - PF4J extension point: `pluginId()`, `parser()`
- `ArtifactParser` - `id()`, `displayName()`, `supports(ArtifactInputDescriptor)`,
  `parse(ArtifactInputDescriptor, InputStream, ArtifactParseContext)`, `iconResourceName()`, `openIconStream()`
- `ArtifactInputDescriptor` - input type, file name, extension, content type hints
- `IArtifactParseContext` - checksum utilities, safe temp workspace, metadata writer helpers

Plugins decide artifact compatibility and produce normalized metadata only; they never write the catalog
directly.

Two different ids are in play, and they're not interchangeable:

- `ArtifactParserPlugin.pluginId()` is PF4J's identifier for the plugin/module itself (used by `list-plugins`
  and friends). It's independent of the parser it wraps.
- `ArtifactParser.id()` is the parser's own stable identifier (e.g. `"maven"`, `"vsix"`) - this is what's
  written into every artifact's `pluginId` catalog field, and what drives URL routing
  (`/artifacts/<id>/...`) and grouping. A plugin could in principle expose a parser whose `id()` differs from
  its `pluginId()`, though today each plugin wraps exactly one parser and the two happen to match.

`ArtifactParser.displayName()` (e.g. `"Maven"`, `"VS Code Extension"`) is what's actually shown in the UI -
parser cards, page headings, artifact card/detail "parser" labels, and search results - never `id()`.

### Parser icons

Every `ArtifactParser` must declare a default icon via `iconResourceName()` (e.g. `"icon.svg"`), returning the
name of a classpath resource placed alongside the parser implementation class - its standard location (e.g.
`com/nf3t/artifactsite/plugin/maven/icon.svg` next to `MavenArtifactParser`). `openIconStream()` (default
method) loads it through the parser class's own classloader, so plugin-supplied icons work the same way inside
PF4J's isolated plugin classloaders.

`parse` caches each loaded parser's icon into the icons cache directory (above), named `<parser id>.<ext>`
(e.g. `maven.svg`, `vsix.svg`), so `generate` can render icons without needing plugins loaded. `generate`
copies cached icons into `assets/icons/` in the generated site and renders them on parser cards, artifact
cards, and artifact detail pages.

### Parser display names

`parse` caches each loaded parser's `displayName()` into the parser display name cache (above) as a JSON
object keyed by `ArtifactParser.id()`, merged with whatever names are already cached (so a parser that isn't
currently installed keeps its last-known display name). `generate` reads that cache and falls back to the raw
parser id for any parser it can't find a cached name for.

## Generated site layout

Rendered from FreeMarker templates in `artifact-site-cli/src/main/resources/templates`
(`layout.ftl`, `index.ftl`, `artifact.ftl`, `tag.ftl`) into `./public`:

- `index.html` - lists unique parsers
- `artifacts/<parser-type>/index.html` - lists unique `<group-id>.<artifact-id>`, using each one's latest version
- `artifacts/<parser-type>/<group-id>.<artifact-id>/index.html` - lists all versions of an artifact, latest first
- `artifacts/<parser-type>/<group-id>.<artifact-id>/<version>/index.html` - artifact detail page: rendered
  README as the main content (or a "no README" note) alongside a sidebar with Resources/Details/Tags cards,
  loosely modeled on Open VSX's extension page
- `tags/<tag>/index.html` - tag-filtered listing
- `assets/styles.css`, `assets/app.js`, `assets/logo.svg`
- `assets/icons/<pluginId>.<ext>` - each parser plugin's default icon, copied from the icons cache
- `search-index.json` - powers the header search box's live results dropdown (vanilla JS, no runtime CSS
  framework)
- `downloads/...` - copies of locally-sourced artifacts only

## Non-goals

- No database, no server-side rendering at request time
- No authentication/authorization layer
- No artifact upload API

## Status

Bootstrap, plugin API, catalog persistence, remote parsing, static generation, and the OpenVSIX-style UI with
client-side search are implemented and working. Remaining work:

- [ ] More parser plugins: VintageStory Mod, Chrome Browser Extension, NiFi NAR (module not yet wired into
      the root `pom.xml`)
- [ ] Hardening: broader input validation, more tests, docs, packaging, release steps

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
- Add DisplayNames / Display Descriptions for artifact types. artifact types (home page should not include recent artifacts)
- hover over on file size shows true bytes.
- Maven description
- Search should give a hint on what it matched against.
- SHA256 stuff should get moved back into cli. All common stuff should happen preprocessor.
