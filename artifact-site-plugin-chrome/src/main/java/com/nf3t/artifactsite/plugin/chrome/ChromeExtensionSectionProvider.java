package com.nf3t.artifactsite.plugin.chrome;

import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.ElementType;
import com.nf3t.artifactsite.api.SectionSpec;

/**
 * Declares the detail page sections and display name for the {@code "chrome-extension"} artifact
 * type {@link ChromeExtensionParser} produces: the packaged README, a manifest summary, and a table
 * of requested permissions.
 */
@Extension
public class ChromeExtensionSectionProvider implements ArtifactSectionProvider {

    /** {@inheritDoc} */
    @Override
    public Map<String, List<SectionSpec>> sections() {
        return Map.of(
                ChromeExtensionParser.ARTIFACT_TYPE_CHROME_EXTENSION,
                List.of(
                        new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.README),
                        new SectionSpec("Manifest", ElementType.MARKDOWN, ChromeExtensionFields.DETAILS),
                        new SectionSpec("Permissions", ElementType.TABLE, ChromeExtensionFields.PERMISSIONS)));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> displayNames() {
        return Map.of(ChromeExtensionParser.ARTIFACT_TYPE_CHROME_EXTENSION, "Chrome Extension");
    }
}
