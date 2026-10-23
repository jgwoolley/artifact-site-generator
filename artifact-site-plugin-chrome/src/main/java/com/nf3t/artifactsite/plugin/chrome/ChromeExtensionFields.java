package com.nf3t.artifactsite.plugin.chrome;

import com.nf3t.artifactsite.api.DatumField;

/**
 * Chrome-extension-specific {@code Datum} fields, rendered by {@link ChromeExtensionSectionProvider}.
 */
public enum ChromeExtensionFields implements DatumField {
    PERMISSIONS("permissions", "table of the permissions the extension requests (Permission/Kind rows)"),
    DETAILS("details", "markdown summary of the extension's manifest (manifest version, minimum Chrome version, etc.)");

    private final String key;
    private final String fieldDescription;

    ChromeExtensionFields(String key, String fieldDescription) {
        this.key = key;
        this.fieldDescription = fieldDescription;
    }

    /** {@inheritDoc} */
    @Override
    public String getNamespace() {
        return "com.nf3t.chrome.extension";
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
