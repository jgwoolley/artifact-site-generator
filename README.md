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
- `artifact-site-plugin-archetype` - Maven archetype that scaffolds a new parser plugin module (see
  [New parser plugins](#new-parser-plugins))

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

Optionally override the home page's title and the site's favicon:

```sh
artifact-site-generator generate --title="Acme Registry" --favicon=./acme-icon.svg
```

- `--title` replaces "Artifact Registry" in the home page's `<title>` and heading only; every other
  page (parser/artifact/tag pages, build info) keeps its own title unaffected.
- `--favicon` replaces the bundled default favicon (`assets/favicon.svg`) with the given image (any
  extension - the MIME type is derived from it). It's used as the browser tab icon and as the header
  icon next to "Artifact Registry" on every page, the fallback icon for any parser or artifact that
  doesn't supply its own (see [Parser icons](#parser-icons)), and the site's PWA manifest icon (see
  [Progressive Web App](#progressive-web-app)).

Both also feed the generated `manifest.webmanifest` (`--title` as its `name`/`short_name`, `--favicon` as its
icon), so the site's installed name/icon (see [Progressive Web App](#progressive-web-app)) stays in sync with
the home page and header.

## XDG paths

- Plugin directory: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/plugins`
- Artifact catalog: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/artifacts.json`
- Remote download cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-cache`
- Remote request config: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/remote-requests.json`
- Parser icon cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/icons`
- Parser display name cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/parser-display-names.json`
- Parser install guide cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/parser-install-guides.json`
- Parser SEO tags cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/parser-seo-tags.json`
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
- `iconData`, `iconFileName` (per-artifact icon override, if any - see [Parser icons](#parser-icons))

Parser-specific metadata sources:

- **Maven**: reads `name`, `description`, `developers`, and `scm` from the embedded
  `META-INF/maven/<group>/<artifact>/pom.xml`.
- **VSIX**: reads `displayName`, `description`, `author`, `contributors`, `repository`, and `icon` from the
  packaged `package.json`, with manifest fallbacks for display name, description, and source URL. Also
  discovers a packaged `readme.md` (case-insensitive; when more than one exists, the one closest to the
  extension's `extension/` root wins) and, when `package.json` declares an `icon` (e.g. `"icon.png"` or
  `"images/icon.png"`, resolved relative to `package.json`'s own directory inside the package), the referenced
  image as this artifact's icon.

### README rendering

A parser can populate `ArtifactMetadata.readme` with the raw markdown text of a README it finds inside the
artifact (only the VSIX parser does this today, via the extension's packaged `readme.md`; the metadata field
itself is generic, so any parser can support it). `generate` renders that markdown to HTML with
commonmark-java and makes it the main content of the artifact detail page, with **raw HTML in the source
markdown escaped rather than passed through** - READMEs come from arbitrary third-party artifacts, so this
keeps a malicious one from injecting a `<script>` or other active content into the generated site. An artifact
with no README shows a plain "No README was found for this artifact." message instead.

## New parser plugins

`artifact-site-plugin-archetype` generates a new parser plugin module (`ArtifactParser` +
`ArtifactParserPlugin` skeleton, POM wired for `provided`-scope CLI dependencies, JAR manifest with
`Plugin-Id`/`Plugin-Version`/`Plugin-Provider`) from the same shape as `artifact-site-plugin-maven` and
`artifact-site-plugin-vsix`:

```sh
mvn archetype:generate \
  -DarchetypeGroupId=com.nf3t \
  -DarchetypeArtifactId=artifact-site-plugin-archetype \
  -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DgroupId=com.example \
  -DartifactId=artifact-site-plugin-example \
  -Dpackage=com.example.artifactsite.plugin.example \
  -DparserId=example \
  -DparserClassName=Example \
  -DparserDisplayName="Example Format"
```

`parserId` is the parser's stable `id()` (written into every parsed artifact's `pluginId` field and used for
URL routing); `parserClassName` is the PascalCase prefix for the generated `${parserClassName}ArtifactParser`
/ `${parserClassName}ArtifactParserPlugin` classes; `parserDisplayName` is the UI-facing name. See the
generated project's own `README.md` for next steps, and the [Plugin API](#plugin-api) section below for the
contract being implemented.

## Plugin API

Defined in `artifact-site-plugins-api`:

- `ArtifactParserPlugin` - PF4J extension point: `pluginId()`, `parser()`
- `ArtifactParser` - `id()`, `displayName()`, `supports(ArtifactInputDescriptor)`,
  `parse(ArtifactInputDescriptor, InputStream, ArtifactParseContext)`, `iconResourceName()`, `openIconStream()`,
  `installGuideResourceName()` (optional), `openInstallGuideStream()`, `seoTags()` (optional)
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
PF4J's isolated plugin classloaders. This is the fallback icon shown for any artifact whose parser doesn't
supply a more specific one.

If a parser's own icon can't be resolved at all (no `iconResourceName()` declared, the declared resource is
missing, or the plugin JAR is binary-incompatible with the current plugin API - see `ParserIconCache`),
`generate` falls back to the site's own favicon (`assets/favicon.svg`, or the image passed to `--favicon`) on
parser cards, artifact cards, and page headings instead of rendering with no icon.

A parser can also override that default on a **per-artifact** basis by populating
`ArtifactMetadata.iconData` (base64-encoded icon bytes) and `iconFileName` (the icon's original file name, used
to derive its extension) from its own `parse()` implementation - no plugin API changes are needed, since these
are plain `ArtifactMetadata` fields any parser can set. The VSIX parser is the first to do this: it reads the
icon referenced by the packaged `package.json`'s `icon` field. `generate` writes each artifact's own icon (when
present) to `assets/icons/artifacts/<parser-type>/<group-id>.<artifact-id>/<version>.<ext>` and uses it in
place of the parser's default icon on that artifact's cards and detail page.

`parse` caches each loaded parser's icon into the icons cache directory (above), named `<parser id>.<ext>`
(e.g. `maven.svg`, `vsix.svg`), so `generate` can render icons without needing plugins loaded. `generate`
copies cached icons into `assets/icons/` in the generated site and renders them on parser cards, artifact
cards, and artifact detail pages.

### Parser display names

`parse` caches each loaded parser's `displayName()` into the parser display name cache (above) as a JSON
object keyed by `ArtifactParser.id()`, merged with whatever names are already cached (so a parser that isn't
currently installed keeps its last-known display name). `generate` reads that cache and falls back to the raw
parser id for any parser it can't find a cached name for.

### Parser install guides

A parser can optionally declare an install guide via `installGuideResourceName()` (e.g. `"install.html"`, next
to `MavenArtifactParser`/`VsixArtifactParser`) - an HTML fragment shown to users in a "How to Install" popup on
the artifact detail page. Unlike a README, the fragment is trusted, parser-author-controlled markup and is
emitted as-is (not escaped), so it can freely use `<pre>`/`<code>` and other markup styled by the site's own
CSS (`.install-guide` shares its typography rules with `.readme`). It may reference `{{groupId}}`,
`{{artifactId}}`, and `{{version}}` placeholders; `generate` substitutes those with the specific artifact's own
(HTML-escaped) values.

`parse` caches each loaded parser's install guide template into the parser install guide cache (above) as a
JSON object keyed by `ArtifactParser.id()`; a parser that declares no guide has any previously cached entry
removed (a deliberate opt-out), while a parser that simply isn't loaded this run keeps its last-known guide.
Code blocks (`<pre>`) in the rendered popup get a "Copy" button (`assets/app.js`), using the Clipboard API with
a `document.execCommand('copy')` fallback for non-secure contexts.

### Parser SEO tags

A parser can optionally declare format-specific SEO keywords via `seoTags()` (e.g. the Maven parser returns
`["maven", "java", "jar", "dependency", "artifact"]`; VSIX returns `["vscode", "visual studio code",
"extension", "vsix"]`) - defaults to none. These are combined with each individual artifact's own `tags` (see
[Data model](#data-model)) into that artifact's `<meta name="keywords">` on its detail page, and are also used
on their own for the parser's index page and (aggregated as parser display names) the home page - see
[SEO](#seo) below.

`parse` caches each loaded parser's `seoTags()` into the parser SEO tags cache (above) as a JSON object keyed
by `ArtifactParser.id()`; a parser that declares no tags has any previously cached entry removed (a deliberate
opt-out), while a parser that simply isn't loaded this run keeps its last-known tags.

## SEO

Every generated page carries baseline SEO meta tags: `<meta name="description">` (from that page's own
description), `<meta name="robots" content="index, follow">`, Open Graph (`og:type`/`og:title`/
`og:description`/`og:image`), and Twitter Card tags, with `og:image`/`twitter:image` falling back to the
site's own favicon when a page has no more specific icon. Artifact detail pages, artifact/parser index pages,
tag pages, and the home page additionally carry `<meta name="keywords">` (see
[Parser SEO tags](#parser-seo-tags) above for what feeds it).

## Progressive Web App

The generated site is installable: `generate` writes a `manifest.webmanifest` (named after the effective home
page title from `--title`, and using the effective favicon from `--favicon` as its icon - see
[`generate`](#generate)) and a `sw.js` service worker at the site root, registered by `assets/app.js` with a
scope covering the whole site (works the same whether hosted at a domain root or a subpath, e.g. a GitHub
Pages project site). The service worker does basic network-first, cache-fallback offline support - since this
generator's page list (one per parsed artifact/version) isn't known ahead of time by a generic script, pages
are cached lazily as they're visited rather than precached.

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
- `build-info.html` - provenance for the generated site: CI/CD provider, source repository/commit link, and
  pipeline/run link, read from GitHub Actions or GitLab CI environment variables at `generate` time (blank on a
  local build with neither set); linked from the "Build Info" link in the header on every page
- `assets/styles.css`, `assets/app.js`, `assets/logo.svg`, `assets/favicon.svg` (also the browser tab icon on
  every page, and the fallback icon for parsers/artifacts with no icon of their own - see
  [Parser icons](#parser-icons))
- `assets/icons/<pluginId>.<ext>` - each parser plugin's default icon, copied from the icons cache
- `assets/icons/artifacts/<parser-type>/<group-id>.<artifact-id>/<version>.<ext>` - per-artifact icon
  overrides (see [Parser icons](#parser-icons)), written only for artifacts whose parser supplied one
- `search-index.json` - powers the header search box's live results dropdown (vanilla JS, no runtime CSS
  framework)
- `manifest.webmanifest`, `sw.js` - PWA support, see [Progressive Web App](#progressive-web-app)
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
- Move Banner stuff to a "product.json file"
