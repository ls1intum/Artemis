package de.tum.in.www1.artemis.repository.converters;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.AttributeConverter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JpaConverterJson<T> implements AttributeConverter<List<T>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Class<T> collectionType = null;

    public JpaConverterJson() {
    }

    public JpaConverterJson(Class<T> collectionType) {
        this.collectionType = collectionType;
    }

    // Converts the Java object to a JSON String for storing in the database
    @Override
    public String convertToDatabaseColumn(List<T> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        }
        catch (Exception ex) {
            // Handle or throw an exception
            return null;
        }
    }

    // Converts the JSON String back to the Java object
    @Override
    public List<T> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, collectionType));
        }
        catch (Exception ex) {
            // Handle or throw an exception
            return null;
        }
    }
}
