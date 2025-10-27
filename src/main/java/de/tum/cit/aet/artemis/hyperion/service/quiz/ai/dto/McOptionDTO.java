package de.tum.cit.aet.artemis.hyperion.service.quiz.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record McOptionDTO(@NotBlank String text, boolean correct, String feedback) {
}
