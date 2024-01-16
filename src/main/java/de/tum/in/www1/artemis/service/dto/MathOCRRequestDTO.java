package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathOCRRequestDTO(String image, Strokes strokes) {

    public record Strokes(Double[][] x, Double[][] y) {
    }
}
