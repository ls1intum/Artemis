package de.tum.cit.aet.artemis.quiz.service.ai.dto;

import java.util.List;

import org.springframework.lang.Nullable;

public record AiQuizGenerationResponseDTO(List<GeneratedMcQuestionDTO> questions, @Nullable List<String> warnings) {
}
