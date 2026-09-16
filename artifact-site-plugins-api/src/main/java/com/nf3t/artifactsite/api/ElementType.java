package com.nf3t.artifactsite.api;

/**
 * The built-in kinds of content a {@link SectionSpec} can render on an artifact detail page.
 */
public enum ElementType {
    /** Renders a {@code Datum} string field as markdown (see {@link Datum#getString(String, String)}). */
    MARKDOWN,
    /** Renders a {@code Datum} record-list field as a multi-column table (see {@link Datum#getRecordList(String, String)}). */
    TABLE
}
