package de.tum.cit.aet.artemis.iris.domain.message;

import java.io.IOException;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

@Converter
public class IrisMessageToolActivityConverter implements AttributeConverter<List<PyrisActivityDTO>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Override
    public String convertToDatabaseColumn(List<PyrisActivityDTO> activities) {
        if (activities == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(activities);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert Iris tool activities to JSON", e);
        }
    }

    @Override
    public List<PyrisActivityDTO> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) {
            return null;
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, PyrisActivityDTO.class);
            return objectMapper.readValue(jsonData, type);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to Iris tool activities", e);
        }
    }
}
