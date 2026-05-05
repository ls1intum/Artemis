package de.tum.cit.aet.artemis.lecture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlidePageNumberMapConverterTest {

    private SlidePageNumberMapConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SlidePageNumberMapConverter();
    }

    @Test
    void convertToDatabaseColumn_shouldSerializeMap() {
        Map<Integer, Integer> map = Map.of(0, 1, 1, 2, 2, -1, 3, 4);
        String json = converter.convertToDatabaseColumn(map);
        assertThat(json).isNotNull().contains("\"0\":1").contains("\"1\":2").contains("\"2\":-1").contains("\"3\":4");
    }

    @Test
    void convertToDatabaseColumn_shouldReturnNullForNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_shouldReturnNullForEmptyMap() {
        assertThat(converter.convertToDatabaseColumn(Collections.emptyMap())).isNull();
    }

    @Test
    void convertToEntityAttribute_shouldDeserializeMap() {
        String json = "{\"0\":1,\"1\":2,\"2\":-1,\"3\":4}";
        Map<Integer, Integer> map = converter.convertToEntityAttribute(json);
        assertThat(map).isNotNull().hasSize(4).containsEntry(0, 1).containsEntry(1, 2).containsEntry(2, -1).containsEntry(3, 4);
    }

    @Test
    void convertToEntityAttribute_shouldReturnEmptyMapForNull() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void convertToEntityAttribute_shouldReturnEmptyMapForEmptyString() {
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void convertToEntityAttribute_shouldReturnEmptyMapForEmptyJson() {
        assertThat(converter.convertToEntityAttribute("{}")).isEmpty();
    }

    @Test
    void roundTrip_shouldPreserveData() {
        Map<Integer, Integer> original = Map.of(0, 1, 1, 2, 2, -1, 3, 4);
        String json = converter.convertToDatabaseColumn(original);
        Map<Integer, Integer> roundTripped = converter.convertToEntityAttribute(json);
        assertThat(roundTripped).isEqualTo(original);
    }
}
