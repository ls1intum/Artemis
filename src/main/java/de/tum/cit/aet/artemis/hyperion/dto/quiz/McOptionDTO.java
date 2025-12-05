package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record McOptionDTO(@NotBlank String text, boolean correct, String feedback) {
}
