package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import jakarta.validation.constraints.NotBlank;

public record McOptionDTO(@NotBlank String text, boolean correct, String feedback) {
}
