package de.tum.cit.aet.artemis.math.domain;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@Converter
public class IntegerListConverter implements AttributeConverter<List<Integer>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Override
    public String convertToDatabaseColumn(List<Integer> list) {
        if (list == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert List<Integer> to JSON", e);
        }
    }

    @Override
    public List<Integer> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            CollectionType type = objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
            return objectMapper.readValue(jsonData, type);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to List<Integer>", e);
        }
    }
}
