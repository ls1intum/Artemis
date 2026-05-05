package de.tum.cit.aet.artemis.lecture.domain;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@Converter
public class SlidePageNumberMapConverter implements AttributeConverter<Map<Integer, Integer>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private static final TypeReference<Map<Integer, Integer>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<Integer, Integer> slidePageNumberMap) {
        if (slidePageNumberMap == null || slidePageNumberMap.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(slidePageNumberMap);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert slide page number map to JSON", e);
        }
    }

    @Override
    public Map<Integer, Integer> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // Handle double-encoded JSON strings (if they exist)
            if (jsonData.startsWith("\"") && jsonData.endsWith("\"")) {
                jsonData = objectMapper.readValue(jsonData, String.class);
            }

            return objectMapper.readValue(jsonData, MAP_TYPE);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to slide page number map", e);
        }
    }
}
