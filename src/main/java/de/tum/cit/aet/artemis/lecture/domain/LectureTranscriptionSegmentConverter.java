package de.tum.cit.aet.artemis.lecture.domain;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Converter
public class LectureTranscriptionSegmentConverter implements AttributeConverter<List<LectureTranscriptionSegment>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<LectureTranscriptionSegment> transcriptionSegments) {
        if (transcriptionSegments == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(transcriptionSegments);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert list of transcription segments to JSON", e);
        }
    }

    @Override
    public List<LectureTranscriptionSegment> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, LectureTranscriptionSegment.class);
            return objectMapper.readValue(jsonData, type);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to list of transcription segments", e);
        }
    }
}
