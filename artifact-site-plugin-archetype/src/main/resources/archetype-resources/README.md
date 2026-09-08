# ${artifactId}

Artifact Site Generator parser plugin for ${parserDisplayName}, generated from the
`artifact-site-plugin-archetype`.

## Implement the parser

1. Edit `src/main/java/.../${parserClassName}ArtifactParser.java`:
   - `supports(...)`: match this format's file name/extension.
   - `parse(...)`: extract metadata into `ArtifactMetadata` and call `context.writeArtifact(...)`.
2. Replace `src/main/resources/.../icon.svg` with this parser's own icon.
3. Edit `src/main/resources/.../install.html` (supports `{{groupId}}`, `{{artifactId}}`, and
   `{{version}}` placeholders), or delete it along with the `installGuideResourceName()` /
   `openInstallGuideStream()` overrides in the parser class if this format has no install guide.
4. Update `artifact-site-plugins-api.version` and `pf4j.version` in `pom.xml` if targeting a
   different Artifact Site Generator release than the one this archetype defaulted to.

## Adding dependencies

This plugin is loaded by pf4j as a single jar, and its classloader only sees this jar's own
contents plus `com.nf3t.artifactsite.api.*` / `org.pf4j.*` from the host CLI. `pom.xml` already
shades this plugin's own dependencies into that jar (via `maven-shade-plugin`), so any dependency
you add beyond `artifact-site-plugins-api` and `pf4j` (both `provided`, supplied by the host) just
works — do not work around a missing dependency by adding it to `artifact-site-cli`'s `pom.xml`
instead.

## Build and install

```sh
mvn package
artifact-site-generator add-plugin ./target/${artifactId}-*.jar
```

See https://github.com/jgwoolley/artifact-site-generator for the full plugin API reference.
