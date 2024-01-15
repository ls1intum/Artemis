package de.tum.in.www1.artemis.service.dto;

public record MathOCRResponseDTO(String text, String latex, Double confidence, String error) {
}
