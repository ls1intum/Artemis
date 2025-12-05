package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AiQuizGenerationResponseDTO(List<GeneratedMcQuestionDTO> questions, List<String> warnings) {
}
