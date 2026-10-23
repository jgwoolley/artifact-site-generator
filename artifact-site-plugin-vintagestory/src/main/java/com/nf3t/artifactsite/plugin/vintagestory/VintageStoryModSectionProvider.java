package com.nf3t.artifactsite.plugin.vintagestory;

import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.ElementType;
import com.nf3t.artifactsite.api.SectionSpec;

/**
 * Declares the detail page sections and display name for the {@code "vintagestory-mod"} artifact
 * type {@link VintageStoryModParser} produces: the packaged README, a {@code modinfo.json} summary,
 * and a table of dependencies.
 */
@Extension
public class VintageStoryModSectionProvider implements ArtifactSectionProvider {

    /** {@inheritDoc} */
    @Override
    public Map<String, List<SectionSpec>> sections() {
        return Map.of(
                VintageStoryModParser.ARTIFACT_TYPE_VINTAGE_STORY_MOD,
                List.of(
                        new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.README),
                        new SectionSpec("Mod Info", ElementType.MARKDOWN, VintageStoryModFields.DETAILS),
                        new SectionSpec("Dependencies", ElementType.TABLE, VintageStoryModFields.DEPENDENCIES)));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> displayNames() {
        return Map.of(VintageStoryModParser.ARTIFACT_TYPE_VINTAGE_STORY_MOD, "Vintage Story Mod");
    }
}
