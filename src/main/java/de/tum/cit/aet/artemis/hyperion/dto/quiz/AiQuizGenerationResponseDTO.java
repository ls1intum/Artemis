package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AiQuizGenerationResponseDTO(@NotNull List<GeneratedQuizQuestionDTO> questions, List<String> warnings) {
}
