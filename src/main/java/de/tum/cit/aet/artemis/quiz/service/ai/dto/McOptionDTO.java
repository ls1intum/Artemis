package de.tum.cit.aet.artemis.quiz.service.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record McOptionDTO(@NotBlank String text, boolean correct, String feedback) {
}
