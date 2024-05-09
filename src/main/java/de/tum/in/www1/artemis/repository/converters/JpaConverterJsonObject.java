package de.tum.in.www1.artemis.repository.converters;

import jakarta.persistence.AttributeConverter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JpaConverterJsonObject implements AttributeConverter<Object, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Converts the Java object to a JSON String for storing in the database
    @Override
    public String convertToDatabaseColumn(Object attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    // Converts the JSON String back to the Java object
    @Override
    public Object convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, new TypeReference<>() {
            });
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
