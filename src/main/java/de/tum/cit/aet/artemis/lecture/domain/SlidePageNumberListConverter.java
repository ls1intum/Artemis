package de.tum.cit.aet.artemis.lecture.domain;

import java.io.IOException;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * JPA converter for storing slide page numbers as a JSON array in the database.
 * Converts between List&lt;Integer&gt; in Java and JSON array string in the database.
 * <p>
 * The list represents page numbers indexed by slide number (0-based):
 * Index 0 = page number for slide 1, Index 1 = page number for slide 2, etc.
 * A value of -1 indicates the slide has no corresponding page number.
 */
@Converter
public class SlidePageNumberListConverter implements AttributeConverter<List<Integer>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private static final TypeReference<List<Integer>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Integer> slidePageNumbers) {
        if (slidePageNumbers == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(slidePageNumbers);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert slide page numbers to JSON", e);
        }
    }

    @Override
    public List<Integer> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) {
            return null;
        }

        try {
            // Handle double-encoded JSON from PyRIS (intentional wire format)
            if (jsonData.startsWith("\"") && jsonData.endsWith("\"")) {
                jsonData = objectMapper.readValue(jsonData, String.class);
            }

            return objectMapper.readValue(jsonData, LIST_TYPE);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to slide page numbers", e);
        }
    }
}
