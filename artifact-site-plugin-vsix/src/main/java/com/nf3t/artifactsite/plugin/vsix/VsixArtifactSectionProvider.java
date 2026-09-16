package com.nf3t.artifactsite.plugin.vsix;

import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.SectionSpec;

/**
 * Declares the display name for the {@code "vsix"} artifact type {@link VsixArtifactParser}
 * produces. No sections are declared - a VSIX's README keeps rendering through the generic
 * fallback (see {@code artifact.ftl}) when an artifact type declares no sections of its own.
 */
@Extension
public class VsixArtifactSectionProvider implements ArtifactSectionProvider {

    /** {@inheritDoc} */
    @Override
    public Map<String, List<SectionSpec>> sections() {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> displayNames() {
        return Map.of(VsixArtifactParser.ARTIFACT_TYPE_VSIX, "VS Code Extension");
    }
}
