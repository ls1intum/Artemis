package de.tum.cit.aet.artemis.quiz.service.ai.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record AiQuizImportRequestDTO(@NotEmpty List<GeneratedMcQuestionDTO> questions) {
}
