package com.nf3t.artifactsite.plugin.maven;

import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.ElementType;
import com.nf3t.artifactsite.api.SectionSpec;

/**
 * Declares the artifact detail page sections for the artifact types {@link MavenArtifactParser}
 * produces: plain {@code "jar"}s (and NiFi Archives, which also carry {@code "jar"}) show their
 * README, while each NiFi extension's own {@code "nifi-extension"} record shows its description
 * plus a table per manifest section (Properties, Relationships, etc.) instead of the pipe-delimited
 * markdown table this used to be hand-built into.
 */
@Extension
public class MavenArtifactSectionProvider implements ArtifactSectionProvider {

    /** {@inheritDoc} */
    @Override
    public Map<String, List<SectionSpec>> sections() {
        return Map.of(
                MavenArtifactParser.ARTIFACT_TYPE_JAR,
                List.of(new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.README)),

                // Intentionally empty for now: a NiFi Archive's own detail page already shows its
                // "jar" section above; this is here to demonstrate that an artifact can carry more
                // than one type without needing NAR-level content of its own yet.
                MavenArtifactParser.ARTIFACT_TYPE_NAR,
                List.of(),

                MavenArtifactParser.ARTIFACT_TYPE_NIFI_EXTENSION,
                List.of(
                        new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.DESCRIPTION),
                        new SectionSpec("Properties", ElementType.TABLE, NarExtensionFields.PROPERTIES),
                        new SectionSpec("Dynamic Properties", ElementType.TABLE, NarExtensionFields.DYNAMIC_PROPERTIES),
                        new SectionSpec("Relationships", ElementType.TABLE, NarExtensionFields.RELATIONSHIPS),
                        new SectionSpec("Dynamic Relationship", ElementType.TABLE, NarExtensionFields.DYNAMIC_RELATIONSHIP),
                        new SectionSpec("Reads Attributes", ElementType.TABLE, NarExtensionFields.READS_ATTRIBUTES),
                        new SectionSpec("Writes Attributes", ElementType.TABLE, NarExtensionFields.WRITES_ATTRIBUTES)));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> displayNames() {
        return Map.of(
                MavenArtifactParser.ARTIFACT_TYPE_JAR, "Java",
                MavenArtifactParser.ARTIFACT_TYPE_NAR, "Nar",
                MavenArtifactParser.ARTIFACT_TYPE_NIFI_EXTENSION, "NiFi Extension");
    }
}
