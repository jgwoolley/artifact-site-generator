package com.nf3t.artifactsite.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DatumField {
	public String getNamespace();
	public String getKey();
	public String getFieldDescription();
	
	public default String setString(Datum datum, String value) { return datum.setString(getNamespace(), getKey(), value);} ;
	public default String getString(Datum datum) { return datum.getString(getNamespace(), getKey());} ;
	public default List<String> getStringList(Datum datum) { return datum.getStringList(getNamespace(), getKey());} ;
	public default Map<String, String> getStringMap(Datum datum) { return datum.getStringMap(getNamespace(), getKey());} ;
	public default List<Map<String, String>> getRecordList(Datum datum) { return datum.getRecordList(getNamespace(), getKey());} ;

	public default Long setLong(Datum datum, Long value) { return datum.setLong(getNamespace(), getKey(), value);} ;
	public default Long getLong(Datum datum) { return datum.getLong(getNamespace(), getKey());} ;
	public default List<Long> getLongList(Datum datum) { return datum.getLongList(getNamespace(), getKey());} ;
	public default Map<String, Long> getLongMap(Datum datum) { return datum.getLongMap(getNamespace(), getKey());} ;

	public default UUID setReference(Datum datum, UUID value) { return datum.setReference(getNamespace(), getKey(), value);} ;
	public default UUID getReference(Datum datum) { return datum.getReference(getNamespace(), getKey());} ;
	
	public default UUID setReference(DatumField otherField, Datum datum, Datum other) {
		final UUID id = datum.getId();
		final UUID otherId = other.getId();
		
		final UUID result = setReference(datum, otherId);
		otherField.setReference(other, id);
		
		return result;
	}
	
	
	public default List<UUID> getReferenceList(Datum datum) { return datum.getReferenceList(getNamespace(), getKey());} ;
	public default Map<String, UUID> getReferenceMap(Datum datum) { return datum.getReferenceMap(getNamespace(), getKey());} ;

	public default ZonedDateTime setDateTime(Datum datum, ZonedDateTime value) { return datum.setDateTime(getNamespace(), getKey(), value);} ;
	public default ZonedDateTime getDateTime(Datum datum) { return datum.getDateTime(getNamespace(), getKey());} ;
	public default List<ZonedDateTime> getDateTimeList(Datum datum) { return datum.getDateTimeList(getNamespace(), getKey());} ;
	public default Map<String, ZonedDateTime> getDateTimeMap(Datum datum) { return datum.getDateTimeMap(getNamespace(), getKey());} ;
}
