package de.tum.cit.aet.artemis.math.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@Converter
public class MathNodeConverter implements AttributeConverter<MathNode, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Override
    public String convertToDatabaseColumn(MathNode mathNode) {
        if (mathNode == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mathNode);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert MathNode to JSON", e);
        }
    }

    @Override
    public MathNode convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonData, MathNode.class);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Could not convert JSON to MathNode", e);
        }
    }
}
