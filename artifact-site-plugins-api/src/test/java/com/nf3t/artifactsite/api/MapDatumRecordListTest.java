package com.nf3t.artifactsite.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * Covers {@link Datum#getRecordList(String, String)} - the fourth list value shape, used by
 * table-shaped detail page sections (see {@link ElementType#TABLE}).
 */
class MapDatumRecordListTest {

    private static final String NAMESPACE = "com.example.test";
    private static final String KEY = "properties";

    @Test
    void autoVivifiesAnEmptyListAndReturnsTheSameLiveInstance() {
        Datum datum = new MapDatum();

        List<Map<String, String>> first = datum.getRecordList(NAMESPACE, KEY);
        assertThat(first).isEmpty();

        first.add(Map.of("Property", "Batch Size", "Value", "10"));

        assertThat(datum.getRecordList(NAMESPACE, KEY))
                .containsExactly(Map.of("Property", "Batch Size", "Value", "10"));
    }

    @Test
    void roundTripsThroughStockJacksonObjectMapperWithNoCustomModules() {
        MapDatum datum = new MapDatum();
        datum.getRecordList(NAMESPACE, KEY).add(Map.of("Property", "Batch Size", "Value", "10"));
        datum.getRecordList(NAMESPACE, KEY).add(Map.of("Property", "Timeout", "Value", "30s"));

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(datum);
        MapDatum restored = objectMapper.readValue(json, MapDatum.class);

        assertThat(restored.getRecordList(NAMESPACE, KEY)).containsExactly(
                Map.of("Property", "Batch Size", "Value", "10"),
                Map.of("Property", "Timeout", "Value", "30s"));
    }
}
