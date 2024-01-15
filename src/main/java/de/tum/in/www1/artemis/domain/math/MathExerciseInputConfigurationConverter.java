package de.tum.in.www1.artemis.domain.math;

import java.io.IOException;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Converter
public class MathExerciseInputConfigurationConverter implements AttributeConverter<MathExerciseInputConfiguration, String> {

    private final Logger log = LoggerFactory.getLogger(MathExerciseInputConfigurationConverter.class);

    @Override
    public String convertToDatabaseColumn(MathExerciseInputConfiguration inputConfiguration) {
        if (inputConfiguration == null) {
            return null;
        }

        String inputConfigurationJson = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            inputConfigurationJson = objectMapper.writeValueAsString(inputConfiguration);
        }
        catch (final JsonProcessingException e) {
            log.error("JSON serialization error", e);
        }

        return inputConfigurationJson;
    }

    @Override
    public MathExerciseInputConfiguration convertToEntityAttribute(String inputConfigurationJson) {
        if (inputConfigurationJson == null) {
            return null;
        }

        MathExerciseInputConfiguration inputConfiguration = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            inputConfiguration = objectMapper.readValue(inputConfigurationJson, MathExerciseInputConfiguration.class);
        }
        catch (final IOException e) {
            log.error("JSON deserialization error", e);
        }

        return inputConfiguration;
    }
}
