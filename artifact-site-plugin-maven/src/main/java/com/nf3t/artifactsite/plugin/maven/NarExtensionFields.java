package com.nf3t.artifactsite.plugin.maven;

import com.nf3t.artifactsite.api.DatumField;

public enum NarExtensionFields implements DatumField {
	HAS_NAR("has_nar", "Is a child of the folowing NiFi Archive File (NAR)"),
	HAS_EXTENSION("has_extension", "NiFi Archive File (NAR) contains the following extension");
	
	private final String key;
	private final String fieldDescription;

	NarExtensionFields(String key, String fieldDescription) {
		this.key = key;
		this.fieldDescription = fieldDescription;
	}

	@Override
	public String getNamespace() {
		// TODO Auto-generated method stub
		return "com.nf3t.nifi.extension";
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public String getFieldDescription() {
		return fieldDescription;
	}

}
