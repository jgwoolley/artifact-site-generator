package com.nf3t.artifactsite.api;

public enum ArtifactFields implements DatumField {
	ID("id", "stable artifact identifier"),
	NAME("name", "human-readable artifact name"),
	GROUP_ID("group_id", "artifact group or publisher id"),
	ARTIFACT_ID("artifact_id", "artifact id (module or extension identifier)"),
	VERSION("version", "artifact version"),
	DESCRIPTION("description", "artifact short description"),
	README("readme", "raw README markdown discovered inside the artifact, if any"),
	AUTHORS("authors", "list of artifact authors"),
	TAGS("tags", "list of artifact tags"),
	LICENSE("license", "artifact license"),
	SOURCE_TYPE("source_type", "artifact source type name"),
	SOURCE_VALUE("source_value", "artifact source value"),
	DOWNLOAD_URL("download_url", "artifact source value path or URL"),
	FILE_NAME("file_name", "original artifact file name"),
	FILE_SIZE("file_size", "file size in bytes"),
	FILE_SHA256("file_sha256", "SHA256 of original file"),
	PLUGIN_ID("plugin_id", "The ID of the plugin that parsed this artifact"),
	SCM_URL("scm_url", "Link to the source code of the artifact"),
	ICON_DATA("icon_data", "base64-encoded bytes of an icon discovered inside this specific artifact, if any. "
			+ "When present, this overrides the parser's default icon ({@link ArtifactParser"
			+ "#iconResourceName()}) for this artifact's cards and detail page. Any parser may set"
			+ " this from its own {@code parse()} implementation; the VSIX parser is the first to"
			+ " do so, reading the icon referenced by the packaged {@code package.json}"),
	ICON_ORIGINAL_FILE_NAME("icon_original_file_name", "original file name (or relative path) of {@link #getIconData()}, used to derive\n"
			+ " the icon's file extension when writing it into the generated site; {@code null}\n"
			+ " when {@link #getIconData()} is unset")
	;
	
	private String key;
	private String fieldDescription;

	ArtifactFields(String key, String fieldDescription) {
		this.key = key;
		this.fieldDescription = fieldDescription;
	}
	
    /** {@inheritDoc} */
	@Override
	public String getNamespace() {
		return "com.nf3t.artifact-site-generator";
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

	/**
	 * Returns the artifact's slug, derived from its {@link #GROUP_ID} and {@link #ARTIFACT_ID}
	 * fields (e.g. {@code "com.example.demo"}), used to group an artifact's versions together and
	 * as the path segment source for its generated site pages.
	 *
	 * @param datum artifact datum
	 * @return artifact slug
	 */
	public static String getArtifactSlug(Datum datum) {
		return GROUP_ID.getString(datum) + "." + ARTIFACT_ID.getString(datum);
	}
}
