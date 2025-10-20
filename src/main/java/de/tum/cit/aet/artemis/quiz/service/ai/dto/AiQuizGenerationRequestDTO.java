package de.tum.cit.aet.artemis.quiz.service.ai.dto;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

public record AiQuizGenerationRequestDTO(@NotNull @Min(1) @Max(20) Integer numberOfQuestions, @NotNull Language language, @Size(max = 500) String topic,
        @Size(max = 500) String promptHint, DifficultyLevel difficultyLevel, AiQuestionSubtype requestedSubtype,    // optional; if null → default MULTI_CORRECT
        Set<Long> competencyIds) {
}
