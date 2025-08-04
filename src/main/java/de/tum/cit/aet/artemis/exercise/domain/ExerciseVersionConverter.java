package de.tum.cit.aet.artemis.exercise.domain;

import java.io.IOException;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Converter for transforming between Exercise objects and ExerciseVersionContent.
 */
@Converter
public class ExerciseVersionConverter implements AttributeConverter<ExerciseVersionContent, String> {

    private static final ObjectMapper objectMapper = createObjectMapper();

    /**
     * Creates a properly configured ObjectMapper for handling ExerciseVersionContent.
     *
     * @return configured ObjectMapper instance
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    @Override
    public String convertToDatabaseColumn(ExerciseVersionContent exerciseVersionContent) {
        if (exerciseVersionContent == null)
            return null;
        try {
            return objectMapper.writeValueAsString(exerciseVersionContent);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert ExerciseVersionContent to JSON", e);
        }
    }

    @Override
    public ExerciseVersionContent convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isEmpty())
            return null;
        try {
            JavaType type = objectMapper.getTypeFactory().constructType(ExerciseVersionContent.class);
            if (jsonData.startsWith("\"") && jsonData.endsWith("\"")) {
                jsonData = objectMapper.readValue(jsonData, String.class);
            }
            return objectMapper.readValue(jsonData, type);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to ExerciseVersionContent", e);
        }
    }
}
