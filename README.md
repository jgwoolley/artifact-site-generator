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
- `artifact-site-plugin-chrome` - Chrome browser extension parser plugin (`.crx` and `.zip`)
- `artifact-site-plugin-vintagestory` - Vintage Story mod parser plugin (`.zip`)
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
- Loads parser plugins and runs **every** one whose `supports(...)` matches the input against a single
  shared artifact record (a `Datum`, see [Data model](#data-model)) - so more than one parser can
  contribute fields to the same artifact, rather than each match producing a separate record.
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
- Parser install guide cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/parser-install-guides.json`
- Parser SEO tags cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/parser-seo-tags.json`
- Artifact type sections cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/artifact-type-sections.json`
- Artifact type display name cache: `${XDG_DATA_HOME:-~/.local/share}/artifact-site-generator/artifact-type-display-names.json`
- Generated static site: `./public` by default (override with `--output`)

## Data model

Each catalog record is a `Datum`: a generic `namespace -> key -> value` bag (see [Plugin API](#plugin-api))
rather than a fixed POJO, so any parser can read or write any field without a shared class to change. The
default implementation is `MapDatum` - in-memory, and JSON-serializable through a stock Jackson
`ObjectMapper` with no custom (de)serializers. Standard fields are addressed through typed `ArtifactFields`
constants (e.g. `ArtifactFields.GROUP_ID.getString(datum)`) rather than getters/setters:

- `ID` (stable unique id, e.g. `<group>:<artifact>:<version>`)
- `NAME`, `ARTIFACT_ID`, `GROUP_ID`, `VERSION` - required
- `DESCRIPTION`, `AUTHORS` (list), `TAGS` (list), `LICENSE`
- `SOURCE_TYPE` (`local` or `remote`), `SOURCE_VALUE` (local path or URL)
- `DOWNLOAD_URL`, `FILE_NAME`, `FILE_SIZE`, `FILE_SHA256`
- `PLUGIN_ID` (which parser produced/last touched this record)
- `ARTIFACT_TYPES` (list of artifact type tags, e.g. `"jar"`/`"nar"` - independent of `PLUGIN_ID`, drives
  which [detail page sections](#detail-page-sections) render for this artifact)
- `README` (raw README markdown discovered inside the artifact, if any - see below)
- `ICON_DATA`, `ICON_ORIGINAL_FILE_NAME` (per-artifact icon override, if any - see
  [Parser icons](#parser-icons))

Because a `Datum` can be shared across every parser that `supports(...)` a given input (see
[`parse`](#parse)), a parser's `parse(...)` implementation should only set/add the fields it owns rather
than assume it's the only contributor to the record.

Parser-specific metadata sources:

- **Maven**: reads `name`, `description`, `developers`, and `scm` from the embedded
  `META-INF/maven/<group>/<artifact>/pom.xml`; tags every artifact with the `"jar"` artifact type, plus
  `"nar"` when it finds a NiFi Archive manifest (see [Detail page sections](#detail-page-sections)).
- **VSIX**: reads `displayName`, `description`, `author`, `contributors`, `repository`, and `icon` from the
  packaged `package.json`, with manifest fallbacks for display name, description, and source URL. Also
  discovers a packaged `readme.md` (case-insensitive; when more than one exists, the one closest to the
  extension's `extension/` root wins) and, when `package.json` declares an `icon` (e.g. `"icon.png"` or
  `"images/icon.png"`, resolved relative to `package.json`'s own directory inside the package), the referenced
  image as this artifact's icon. Tags every artifact with the `"vsix"` artifact type.
- **Chrome**: reads a `.crx` (CRX2 and CRX3; the header is skipped and the extension id taken from it) or an
  unpacked-extension `.zip`, then `manifest.json` (comments and trailing commas allowed). Resolves
  `__MSG_key__` names/descriptions from `_locales/` (default locale, then `en`, then any), and picks the extension
  id as the artifact id (from the CRX header, else the manifest's `key`, else a slug of the name) under the fixed
  `chrome` group. Also reads `author`, `homepage_url`, the largest declared icon, and a packaged `README.md`;
  lists `permissions`, `host_permissions`, optional permissions, and content-script match patterns in a
  Permissions table, plus a Manifest summary. Tags every artifact with the `"chrome-extension"` artifact type
  and a `manifest-vN` tag.
- **Vintage Story**: reads `modinfo.json` from a mod `.zip` (leniently, like the game: comments, trailing commas,
  case-insensitive property names; `modid` falls back to the normalized `name`) under the fixed `vintagestory`
  group. Also reads `authors`/`contributors`, `website`, `modicon.*`, a packaged `README.md`, and lists
  `dependencies` (object or legacy array form) in a Dependencies table, plus a Mod Info summary (type, side,
  required-on flags, assemblies, asset domains). Tags every artifact with the `"vintagestory-mod"` artifact type.

Both generic-`.zip` parsers (Chrome and Vintage Story) silently skip a zip that lacks their manifest, so they can
coexist with each other on the same input.

### README rendering

A parser can populate `ArtifactFields.README` on the artifact's `Datum` with the raw markdown text of a
README it finds inside the artifact (only the VSIX parser does this today, via the extension's packaged
`readme.md`; the field itself is generic, so any parser can support it). `generate` renders that markdown to HTML with
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

`parserId` is the parser's stable `id()` (written into every parsed artifact's `ArtifactFields.PLUGIN_ID`
field and used for URL routing); `parserClassName` is the PascalCase prefix for the generated `${parserClassName}ArtifactParser`
/ `${parserClassName}ArtifactParserPlugin` classes; `parserDisplayName` is the UI-facing name. See the
generated project's own `README.md` for next steps, and the [Plugin API](#plugin-api) section below for the
contract being implemented.

## Plugin API

Defined in `artifact-site-plugins-api`:

- `ArtifactParserPlugin` - PF4J extension point: `pluginId()`, `parser()`
- `ArtifactParser` - `id()`, `displayName()`, `supports(Datum)`,
  `parse(Datum, Path, IArtifactParseContext)`, `iconResourceName()`, `openIconStream()`,
  `installGuideResourceName()` (optional), `openInstallGuideStream()`, `seoTags()` (optional)
- `ArtifactSectionProvider` - a separate PF4J extension point declaring [detail page
  sections](#detail-page-sections) per artifact type: `sections()` returns
  `Map<String, List<SectionSpec>>`
- `Datum` - a generic `namespace -> key -> value` artifact record (String/Long/UUID/`ZonedDateTime`
  scalars, lists, maps, and record-lists - `List<Map<String, String>>`, for table-shaped data); list/map/
  record-list getters auto-vivify (create and store an empty collection the first time they're read), so a
  parser populates one by mutating the returned collection directly
- `DatumField` / `ArtifactFields` - typed accessors over a `Datum` (e.g. `ArtifactFields.GROUP_ID`), so
  parsers and the CLI address fields by a shared, documented constant instead of a raw string key
- `MapDatum` - the default `Datum` implementation: in-memory, and JSON-serializable through a stock Jackson
  `ObjectMapper` (no custom modules needed, even for `UUID`/`ZonedDateTime`)
- `IArtifactParseContext` - `createDatum()` (datum factory, defaults to `MapDatum`), `writeArtifact(Datum,
  Path)` (catalog writer; the `Path` is the same local content path passed into `parse(...)`)

Plugins decide artifact compatibility and produce normalized metadata only; they never write the catalog
directly - they go through `IArtifactParseContext.writeArtifact(...)`. Since every parser whose
`supports(...)` matches a given input receives and enriches the *same* `Datum` (see
[`parse`](#parse)/[Data model](#data-model)), calling `writeArtifact(...)` more than once for it (e.g.
because two parsers both matched) is expected and harmless - it just re-upserts the same record.

Two different ids are in play, and they're not interchangeable:

- `ArtifactParserPlugin.pluginId()` is PF4J's identifier for the plugin/module itself (used by `list-plugins`
  and friends). It's independent of the parser it wraps.
- `ArtifactParser.id()` is the parser's own stable identifier (e.g. `"maven"`, `"vsix"`) - this is what's
  written into every artifact's `ArtifactFields.PLUGIN_ID` catalog field, and what keys the parser-level
  icon/install-guide/SEO-tag caches below. A plugin could in principle expose a parser whose `id()` differs
  from its `pluginId()`, though today each plugin wraps exactly one parser and the two happen to match.

Neither `id()` nor `ArtifactParser.displayName()` drives the generated site's URL structure or its
UI labels any more (see [Generated site layout](#generated-site-layout)) - both are superseded there by
`ArtifactFields.ARTIFACT_TYPES` and `ArtifactSectionProvider.displayNames()` (see [Artifact type display
names](#artifact-type-display-names)). `displayName()` remains on the interface as a documented part of a
parser's identity, but nothing in `generate` reads it today.

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
`ArtifactFields.ICON_DATA` (base64-encoded icon bytes) and `ICON_ORIGINAL_FILE_NAME` (the icon's original
file name, used to derive its extension) from its own `parse()` implementation - no plugin API changes are
needed, since these are plain `Datum` fields any parser can set. The VSIX parser is the first to do this: it reads the
icon referenced by the packaged `package.json`'s `icon` field. `generate` writes each artifact's own icon (when
present) to `assets/icons/artifacts/<group-id>/<artifact-id>/<version>.<ext>` and uses it in
place of the parser's default icon on that artifact's cards and detail page.

`parse` caches each loaded parser's icon into the icons cache directory (above), named `<parser id>.<ext>`
(e.g. `maven.svg`, `vsix.svg`), so `generate` can render icons without needing plugins loaded. `generate`
copies cached icons into `assets/icons/` in the generated site and renders them on artifact type cards,
artifact cards, and artifact detail pages.

### Artifact type display names

A plugin declares the UI-facing display name for each artifact type it produces (e.g. `"jar" -> "Java"`,
`"nar" -> "Nar"`) via `ArtifactSectionProvider.displayNames()` - the same extension point used for [detail
page sections](#detail-page-sections) below. `parse` caches every loaded provider's declared names into the
artifact type display name cache (above) as a JSON object keyed by artifact type, merged with whatever names
are already cached (so a type whose provider isn't currently loaded keeps its last-known display name).
`generate` reads that cache and falls back to the raw type key for any type it can't find a cached name for,
using it for the "Artifact Types" section on the home page, each type's own browsing page (see [Generated
site layout](#generated-site-layout)), and the single winning-type label shown on an artifact's own card and
detail-page badge.

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
"extension", "vsix"]`) - defaults to none. An artifact detail page's `<meta name="keywords">` combines its own
`tags` (see [Data model](#data-model)), every one of its artifact type display names (see [Artifact type
display names](#artifact-type-display-names) - not just the one type that wins its visible badge), and its
own parser's `seoTags()` - see [SEO](#seo) below.

`parse` caches each loaded parser's `seoTags()` into the parser SEO tags cache (above) as a JSON object keyed
by `ArtifactParser.id()`; a parser that declares no tags has any previously cached entry removed (a deliberate
opt-out), while a parser that simply isn't loaded this run keeps its last-known tags.

### Detail page sections

An artifact's detail page main content (normally the rendered README, or a "no README" placeholder) can
instead be built from one or more declared **sections**, driven by `ArtifactFields.ARTIFACT_TYPES` rather
than `PLUGIN_ID` - so a single parser can produce artifacts of more than one type (e.g. the Maven parser's
NiFi Archives are both `"jar"` and `"nar"`), each with its own rendering, and an artifact carrying more than
one type renders one block of sections per type, in the order the types are listed on it.

A plugin declares sections via a separate `ArtifactSectionProvider` extension (independent of
`ArtifactParserPlugin`/`ArtifactParser` - a plugin can register both), whose `sections()` returns a
`Map<String, List<SectionSpec>>` keyed by artifact type. Each `SectionSpec` names an optional heading, an
`ElementType`, and the `Datum` field (namespace + key) it reads from:

- `MARKDOWN` - renders a `Datum` string field as markdown (generalizes README rendering to any field).
- `TABLE` - renders a `Datum` record-list field (`Datum.getRecordList(namespace, key)`, a
  `List<Map<String, String>>`) as a multi-column table; the column headers are the union of each row's own
  keys, in first-seen order, so a plugin controls headers simply by choosing consistent map keys.

A section that would render nothing (a blank markdown field, or an empty record list) is omitted rather than
shown empty. When an artifact's types produce no sections at all, its detail page falls back to the original
README-or-placeholder rendering, so this feature is purely additive and opt-in per parser.

`parse` caches every loaded provider's declared sections into the artifact type sections cache (above), keyed
by artifact type rather than by parser or plugin id (since one provider can declare sections for several
types at once). For each artifact type a currently loaded provider mentions, that type's cached entry is
replaced outright, or removed when the provider now declares an empty list for it (a deliberate opt-out); a
type no currently loaded provider mentions at all keeps whatever was cached before, the same convention used
by install guides and SEO tags above. The Maven plugin's `MavenArtifactSectionProvider` is the reference
implementation: it declares a `"jar"` section for the README, and a `"nifi-extension"` section per bundled
NiFi extension showing its description plus a table each for Properties, Dynamic Properties, Relationships,
Dynamic Relationship, Reads Attributes, and Writes Attributes - replacing what used to be a hand-built,
pipe-delimited markdown table jammed into `README`.

## SEO

Every generated page carries baseline SEO meta tags: `<meta name="description">` (from that page's own
description), `<meta name="robots" content="index, follow">`, Open Graph (`og:type`/`og:title`/
`og:description`/`og:image`), and Twitter Card tags, with `og:image`/`twitter:image` falling back to the
site's own favicon when a page has no more specific icon. Artifact detail pages, artifact index pages, tag
pages, type pages, and the home page additionally carry `<meta name="keywords">` (see [Parser SEO
tags](#parser-seo-tags) above for what feeds it).

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
(`layout.ftl`, `index.ftl`, `artifact.ftl`, `tag.ftl` - reused as-is for both tag and type pages, since
they're the same generic "cards for a cross-cutting label" shape) into `./public`:

Artifacts are routed by identity, not by parser or type: `<group-id>`/`<artifact-id>` are always two
separate path segments. "Browse by type" (an artifact's `ArtifactFields.ARTIFACT_TYPES`) is a cross-cutting
classification layered on top - structurally identical to tag pages - so an artifact carrying more than one
type (e.g. a NiFi Archive is both `"jar"` and `"nar"`) appears on every one of its type pages while still
having exactly one canonical detail page.

- `index.html` - home page: "Artifact Types" cards (one per distinct type, linking to its `types/` page
  below) and a single row of up to 5 artifacts (latest version of each, alphabetical; the rest are reachable via type pages and search)
- `artifacts/<group-id>/<artifact-id>/index.html` - lists all versions of an artifact, latest first
- `artifacts/<group-id>/<artifact-id>/<version>/index.html` - artifact detail page: rendered README (or
  declared [sections](#detail-page-sections), or a "no README" note) as the main content alongside a sidebar
  with Resources/Details/Tags cards, loosely modeled on Open VSX's extension page; its badge/card label shows
  the single winning artifact type (see [Artifact type display names](#artifact-type-display-names)), while
  its `<meta name="keywords">` merges *every* type it carries
- `types/<type>/index.html` - listing of every artifact carrying that type, latest version of each (same
  shape as a tag page)
- `tags/<tag>/index.html` - tag-filtered listing
- `build-info.html` - provenance for the generated site: CI/CD provider, source repository/commit link, and
  pipeline/run link, read from GitHub Actions or GitLab CI environment variables at `generate` time (blank on a
  local build with neither set); linked from the "Build Info" link in the header on every page
- `assets/styles.css`, `assets/app.js`, `assets/logo.svg`, `assets/favicon.svg` (also the browser tab icon on
  every page, and the fallback icon for parsers/artifacts with no icon of their own - see
  [Parser icons](#parser-icons))
- `assets/icons/<pluginId>.<ext>` - each parser plugin's default icon, copied from the icons cache
- `assets/icons/artifacts/<group-id>/<artifact-id>/<version>.<ext>` - per-artifact icon
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

- [ ] More parser plugins: NiFi NAR (module not yet wired into the root `pom.xml`)
- [ ] Hardening: broader input validation, more tests, docs, packaging, release steps

## TODO

- Add java modules to src code?
- More sensible commands like "artifact add" "plugin add" "artifact clear" "plugin clear"s
- Add url to background href / a element.
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
