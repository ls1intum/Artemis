package de.tum.cit.aet.artemis.hyperion.service.quiz.ai.dto;

import java.util.List;

import org.springframework.lang.Nullable;

public record AiQuizGenerationResponseDTO(List<GeneratedMcQuestionDTO> questions, @Nullable List<String> warnings) {
}
