package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.SlidePageNumberListConverter;

class SlidePageNumberListConverterTest {

    private SlidePageNumberListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SlidePageNumberListConverter();
    }

    @Test
    void convertToDatabaseColumn_shouldConvertListToJson() {
        List<Integer> slidePageNumbers = List.of(1, 2, 3, 4, 5);

        String json = converter.convertToDatabaseColumn(slidePageNumbers);

        assertThat(json).isEqualTo("[1,2,3,4,5]");
    }

    @Test
    void convertToDatabaseColumn_shouldHandleNullInput() {
        String json = converter.convertToDatabaseColumn(null);

        assertThat(json).isNull();
    }

    @Test
    void convertToDatabaseColumn_shouldHandleEmptyList() {
        List<Integer> emptyList = List.of();

        String json = converter.convertToDatabaseColumn(emptyList);

        assertThat(json).isEqualTo("[]");
    }

    @Test
    void convertToDatabaseColumn_shouldHandleListWithNegativeValues() {
        List<Integer> slidePageNumbers = List.of(1, -1, 3, -1, 5);

        String json = converter.convertToDatabaseColumn(slidePageNumbers);

        assertThat(json).isEqualTo("[1,-1,3,-1,5]");
    }

    @Test
    void convertToEntityAttribute_shouldConvertJsonToList() {
        String json = "[1,2,3,4,5]";

        List<Integer> slidePageNumbers = converter.convertToEntityAttribute(json);

        assertThat(slidePageNumbers).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void convertToEntityAttribute_shouldHandleNullInput() {
        List<Integer> slidePageNumbers = converter.convertToEntityAttribute(null);

        assertThat(slidePageNumbers).isNull();
    }

    @Test
    void convertToEntityAttribute_shouldHandleEmptyString() {
        List<Integer> slidePageNumbers = converter.convertToEntityAttribute("");

        assertThat(slidePageNumbers).isNull();
    }

    @Test
    void convertToEntityAttribute_shouldHandleEmptyJsonArray() {
        String json = "[]";

        List<Integer> slidePageNumbers = converter.convertToEntityAttribute(json);

        assertThat(slidePageNumbers).isEmpty();
    }

    @Test
    void convertToEntityAttribute_shouldHandleListWithNegativeValues() {
        String json = "[1,-1,3,-1,5]";

        List<Integer> slidePageNumbers = converter.convertToEntityAttribute(json);

        assertThat(slidePageNumbers).containsExactly(1, -1, 3, -1, 5);
    }

    @Test
    void convertToEntityAttribute_shouldThrowExceptionForInvalidJson() {
        String invalidJson = "not a valid json";

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> converter.convertToEntityAttribute(invalidJson))
                .withMessageContaining("Could not convert JSON to slide page numbers");
    }

    @Test
    void convertToEntityAttribute_shouldThrowExceptionForInvalidJsonStructure() {
        String invalidJson = "{\"key\": \"value\"}";

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> converter.convertToEntityAttribute(invalidJson))
                .withMessageContaining("Could not convert JSON to slide page numbers");
    }

    @Test
    void roundTrip_shouldPreserveData() {
        List<Integer> original = List.of(10, 20, 30, -1, 40);

        String json = converter.convertToDatabaseColumn(original);
        List<Integer> result = converter.convertToEntityAttribute(json);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void roundTrip_shouldPreserveEmptyList() {
        List<Integer> original = List.of();

        String json = converter.convertToDatabaseColumn(original);
        List<Integer> result = converter.convertToEntityAttribute(json);

        assertThat(result).isEmpty();
    }
}
