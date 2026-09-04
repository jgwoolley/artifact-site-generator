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

## Build and install

```sh
mvn package
artifact-site-generator add-plugin ./target/${artifactId}-*.jar
```

See https://github.com/jgwoolley/artifact-site-generator for the full plugin API reference.
