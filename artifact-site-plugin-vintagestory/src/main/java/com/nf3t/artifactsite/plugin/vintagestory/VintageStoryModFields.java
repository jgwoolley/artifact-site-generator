package com.nf3t.artifactsite.plugin.vintagestory;

import com.nf3t.artifactsite.api.DatumField;

/**
 * Vintage-Story-specific {@code Datum} fields, rendered by {@link VintageStoryModSectionProvider}.
 */
public enum VintageStoryModFields implements DatumField {
    DEPENDENCIES("dependencies", "table of the mods/game systems this mod depends on (Dependency/Version rows)"),
    DETAILS("details", "markdown summary of the mod's modinfo.json (type, side, required-on flags, contents)");

    private final String key;
    private final String fieldDescription;

    VintageStoryModFields(String key, String fieldDescription) {
        this.key = key;
        this.fieldDescription = fieldDescription;
    }

    /** {@inheritDoc} */
    @Override
    public String getNamespace() {
        return "com.nf3t.vintagestory.mod";
    }

    /** {@inheritDoc} */
    @Override
    public String getKey() {
        return key;
    }

    /** {@inheritDoc} */
    @Override
    public String getFieldDescription() {
        return fieldDescription;
    }
}
