package de.tum.cit.aet.artemis.proof.domain;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO.DerivationStepDTO;

/**
 * JPA {@link AttributeConverter} that serialises {@code List<List<DerivationStepDTO>>} to/from a
 * JSON {@code longtext} column. Each inner list is one complete example derivation.
 */
@Converter
public class ExampleDerivationsConverter implements AttributeConverter<List<List<DerivationStepDTO>>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private static final JavaType DERIVATIONS_TYPE;

    static {
        JavaType stepType = objectMapper.getTypeFactory().constructType(DerivationStepDTO.class);
        JavaType innerListType = objectMapper.getTypeFactory().constructCollectionType(List.class, stepType);
        DERIVATIONS_TYPE = objectMapper.getTypeFactory().constructCollectionType(List.class, innerListType);
    }

    @Override
    public String convertToDatabaseColumn(List<List<DerivationStepDTO>> derivations) {
        if (derivations == null || derivations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(derivations);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert example derivations to JSON", e);
        }
    }

    @Override
    public List<List<DerivationStepDTO>> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(jsonData, DERIVATIONS_TYPE);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to example derivations", e);
        }
    }
}
