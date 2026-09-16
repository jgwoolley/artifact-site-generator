package com.nf3t.artifactsite.api;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simple {@link Datum} backed by nested {@code namespace -> key -> value} maps, one map per
 * value shape and type. Every backing map is declared with a concrete generic type (not a type
 * parameter), and every value type (including {@link UUID} and {@link ZonedDateTime}) is stored
 * natively, so the whole object round-trips through a stock Jackson {@code ObjectMapper} with no
 * custom (de)serializers or extra modules (e.g. jackson-datatype-jsr310).
 *
 * <p>Scalar accessors ({@code getString}/{@code getLong}/{@code getReference}/{@code
 * getDateTime}) return {@code null} when unset. List, map, and record-list accessors instead
 * create and store an empty {@link List}/{@link Map} the first time they're read for a given
 * namespace/key, and return that same live instance thereafter, so callers can populate a field by
 * mutating the returned collection directly (e.g. {@code datum.getStringList(ns, key).add("value")})
 * without a separate {@code set} call.
 */
public class MapDatum implements Datum {

    private UUID id;

    private Map<String, Map<String, String>> stringFields = new LinkedHashMap<>();
    private Map<String, Map<String, List<String>>> stringListFields = new LinkedHashMap<>();
    private Map<String, Map<String, Map<String, String>>> stringMapFields = new LinkedHashMap<>();
    private Map<String, Map<String, List<Map<String, String>>>> recordListFields = new LinkedHashMap<>();

    private Map<String, Map<String, Long>> longFields = new LinkedHashMap<>();
    private Map<String, Map<String, List<Long>>> longListFields = new LinkedHashMap<>();
    private Map<String, Map<String, Map<String, Long>>> longMapFields = new LinkedHashMap<>();

    private Map<String, Map<String, UUID>> referenceFields = new LinkedHashMap<>();
    private Map<String, Map<String, List<UUID>>> referenceListFields = new LinkedHashMap<>();
    private Map<String, Map<String, Map<String, UUID>>> referenceMapFields = new LinkedHashMap<>();

    private Map<String, Map<String, ZonedDateTime>> dateTimeFields = new LinkedHashMap<>();
    private Map<String, Map<String, List<ZonedDateTime>>> dateTimeListFields = new LinkedHashMap<>();
    private Map<String, Map<String, Map<String, ZonedDateTime>>> dateTimeMapFields = new LinkedHashMap<>();

    /** Creates a datum with a freshly generated random id. */
    public MapDatum() {
        this(UUID.randomUUID());
    }

    /** Creates a datum with the given id (e.g. one restored from storage). */
    public MapDatum(UUID id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override
    public UUID getId() {
        return id;
    }

    /** @param id datum identifier; used by Jackson when restoring a datum from storage */
    public void setId(UUID id) {
        this.id = id;
    }

    public Map<String, Map<String, String>> getStringFields() {
        return stringFields;
    }

    public void setStringFields(Map<String, Map<String, String>> stringFields) {
        this.stringFields = stringFields;
    }

    public Map<String, Map<String, List<String>>> getStringListFields() {
        return stringListFields;
    }

    public void setStringListFields(Map<String, Map<String, List<String>>> stringListFields) {
        this.stringListFields = stringListFields;
    }

    public Map<String, Map<String, Map<String, String>>> getStringMapFields() {
        return stringMapFields;
    }

    public void setStringMapFields(Map<String, Map<String, Map<String, String>>> stringMapFields) {
        this.stringMapFields = stringMapFields;
    }

    public Map<String, Map<String, List<Map<String, String>>>> getRecordListFields() {
        return recordListFields;
    }

    public void setRecordListFields(Map<String, Map<String, List<Map<String, String>>>> recordListFields) {
        this.recordListFields = recordListFields;
    }

    public Map<String, Map<String, Long>> getLongFields() {
        return longFields;
    }

    public void setLongFields(Map<String, Map<String, Long>> longFields) {
        this.longFields = longFields;
    }

    public Map<String, Map<String, List<Long>>> getLongListFields() {
        return longListFields;
    }

    public void setLongListFields(Map<String, Map<String, List<Long>>> longListFields) {
        this.longListFields = longListFields;
    }

    public Map<String, Map<String, Map<String, Long>>> getLongMapFields() {
        return longMapFields;
    }

    public void setLongMapFields(Map<String, Map<String, Map<String, Long>>> longMapFields) {
        this.longMapFields = longMapFields;
    }

    public Map<String, Map<String, UUID>> getReferenceFields() {
        return referenceFields;
    }

    public void setReferenceFields(Map<String, Map<String, UUID>> referenceFields) {
        this.referenceFields = referenceFields;
    }

    public Map<String, Map<String, List<UUID>>> getReferenceListFields() {
        return referenceListFields;
    }

    public void setReferenceListFields(Map<String, Map<String, List<UUID>>> referenceListFields) {
        this.referenceListFields = referenceListFields;
    }

    public Map<String, Map<String, Map<String, UUID>>> getReferenceMapFields() {
        return referenceMapFields;
    }

    public void setReferenceMapFields(Map<String, Map<String, Map<String, UUID>>> referenceMapFields) {
        this.referenceMapFields = referenceMapFields;
    }

    public Map<String, Map<String, ZonedDateTime>> getDateTimeFields() {
        return dateTimeFields;
    }

    public void setDateTimeFields(Map<String, Map<String, ZonedDateTime>> dateTimeFields) {
        this.dateTimeFields = dateTimeFields;
    }

    public Map<String, Map<String, List<ZonedDateTime>>> getDateTimeListFields() {
        return dateTimeListFields;
    }

    public void setDateTimeListFields(Map<String, Map<String, List<ZonedDateTime>>> dateTimeListFields) {
        this.dateTimeListFields = dateTimeListFields;
    }

    public Map<String, Map<String, Map<String, ZonedDateTime>>> getDateTimeMapFields() {
        return dateTimeMapFields;
    }

    public void setDateTimeMapFields(Map<String, Map<String, Map<String, ZonedDateTime>>> dateTimeMapFields) {
        this.dateTimeMapFields = dateTimeMapFields;
    }

    /** Sets a scalar value, returning whatever was previously stored (or {@code null}). */
    private static <V> V putScalar(Map<String, Map<String, V>> store, String namespace, String key, V value) {
        return store.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>()).put(key, value);
    }

    private static <V> V getScalar(Map<String, Map<String, V>> store, String namespace, String key) {
        Map<String, V> byKey = store.get(namespace);
        return byKey == null ? null : byKey.get(key);
    }

    /** Returns the stored list, creating and storing an empty one first if none exists yet. */
    private static <V> List<V> getOrCreateList(Map<String, Map<String, List<V>>> store, String namespace, String key) {
        return store.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new ArrayList<>());
    }

    /** Returns the stored map, creating and storing an empty one first if none exists yet. */
    private static <V> Map<String, V> getOrCreateMap(Map<String, Map<String, Map<String, V>>> store, String namespace, String key) {
        return store.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new LinkedHashMap<>());
    }

    @Override
    public String setString(String namespace, String key, String value) {
        return putScalar(stringFields, namespace, key, value);
    }

    @Override
    public String getString(String namespace, String key) {
        return getScalar(stringFields, namespace, key);
    }

    @Override
    public List<String> getStringList(String namespace, String key) {
        return getOrCreateList(stringListFields, namespace, key);
    }

    @Override
    public Map<String, String> getStringMap(String namespace, String key) {
        return getOrCreateMap(stringMapFields, namespace, key);
    }

    @Override
    public List<Map<String, String>> getRecordList(String namespace, String key) {
        return getOrCreateList(recordListFields, namespace, key);
    }

    @Override
    public Long setLong(String namespace, String key, Long value) {
        return putScalar(longFields, namespace, key, value);
    }

    @Override
    public Long getLong(String namespace, String key) {
        return getScalar(longFields, namespace, key);
    }

    @Override
    public List<Long> getLongList(String namespace, String key) {
        return getOrCreateList(longListFields, namespace, key);
    }

    @Override
    public Map<String, Long> getLongMap(String namespace, String key) {
        return getOrCreateMap(longMapFields, namespace, key);
    }

    @Override
    public UUID setReference(String namespace, String key, UUID value) {
        return putScalar(referenceFields, namespace, key, value);
    }

    @Override
    public UUID getReference(String namespace, String key) {
        return getScalar(referenceFields, namespace, key);
    }

    @Override
    public List<UUID> getReferenceList(String namespace, String key) {
        return getOrCreateList(referenceListFields, namespace, key);
    }

    @Override
    public Map<String, UUID> getReferenceMap(String namespace, String key) {
        return getOrCreateMap(referenceMapFields, namespace, key);
    }

    @Override
    public ZonedDateTime setDateTime(String namespace, String key, ZonedDateTime value) {
        return putScalar(dateTimeFields, namespace, key, value);
    }

    @Override
    public ZonedDateTime getDateTime(String namespace, String key) {
        return getScalar(dateTimeFields, namespace, key);
    }

    @Override
    public List<ZonedDateTime> getDateTimeList(String namespace, String key) {
        return getOrCreateList(dateTimeListFields, namespace, key);
    }

    @Override
    public Map<String, ZonedDateTime> getDateTimeMap(String namespace, String key) {
        return getOrCreateMap(dateTimeMapFields, namespace, key);
    }
}
