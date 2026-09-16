package com.nf3t.artifactsite.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Datum {
	
	public UUID getId();
	
	public String setString(String namespace, String key, String value);
	public String getString(String namespace, String key);
	/** @return the stored list, creating and storing an empty one first if none exists yet */
	public List<String> getStringList(String namespace, String key);
	/** @return the stored map, creating and storing an empty one first if none exists yet */
	public Map<String, String> getStringMap(String namespace, String key);
	/**
	 * A list of multi-column records (e.g. a table row per NiFi property, each with a "Property"
	 * and "Description" column), used by table-shaped detail page sections. Each record is a flat
	 * {@code column name -> value} map.
	 *
	 * @return the stored record list, creating and storing an empty one first if none exists yet
	 */
	public List<Map<String, String>> getRecordList(String namespace, String key);

	public Long setLong(String namespace, String key, Long value);
	public Long getLong(String namespace, String key);
	/** @return the stored list, creating and storing an empty one first if none exists yet */
	public List<Long> getLongList(String namespace, String key);
	/** @return the stored map, creating and storing an empty one first if none exists yet */
	public Map<String, Long> getLongMap(String namespace, String key);

	public UUID setReference(String namespace, String key, UUID value);
	public UUID getReference(String namespace, String key);
	/** @return the stored list, creating and storing an empty one first if none exists yet */
	public List<UUID> getReferenceList(String namespace, String key);
	/** @return the stored map, creating and storing an empty one first if none exists yet */
	public Map<String, UUID> getReferenceMap(String namespace, String key);

	public ZonedDateTime setDateTime(String namespace, String key, ZonedDateTime value);
	public ZonedDateTime getDateTime(String namespace, String key);
	/** @return the stored list, creating and storing an empty one first if none exists yet */
	public List<ZonedDateTime> getDateTimeList(String namespace, String key);
	/** @return the stored map, creating and storing an empty one first if none exists yet */
	public Map<String, ZonedDateTime> getDateTimeMap(String namespace, String key);
}
