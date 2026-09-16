package com.nf3t.artifactsite.plugin.maven;

import com.nf3t.artifactsite.api.DatumField;

public enum NarExtensionFields implements DatumField {
	HAS_NAR("has_nar", "Is a child of the folowing NiFi Archive File (NAR)"),
	HAS_EXTENSION("has_extension", "NiFi Archive File (NAR) contains the following extension"),
	PROPERTIES("properties", "table of this extension's declared properties (Property/Value rows)"),
	RELATIONSHIPS("relationships", "table of this extension's declared relationships (Relationship/Value rows)"),
	DYNAMIC_PROPERTIES("dynamic_properties", "table of this extension's dynamic properties (Dynamic Property/Value rows)"),
	DYNAMIC_RELATIONSHIP("dynamic_relationship", "table of this extension's dynamic relationship (Dynamic Relationship/Value rows)"),
	READS_ATTRIBUTES("reads_attributes", "table of attributes this extension reads (Reads Attribute/Value rows)"),
	WRITES_ATTRIBUTES("writes_attributes", "table of attributes this extension writes (Writes Attribute/Value rows)");

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
