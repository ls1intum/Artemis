package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AiQuizGenerationRequestDTO(@Size(max = 500) String topic, @NotNull @Size(min = 1, max = 10) Integer numberOfQuestions, @NotNull Language language,
        @NotNull DifficultyLevel difficultyLevel, @NotNull AiQuestionSubtype requestedSubtype, @Size(max = 500) String promptHint) {

    public Map<String, String> toTemplateVariables() {
        return Map.of("topic", topic != null ? topic : "", "numberOfQuestions", String.valueOf(numberOfQuestions), "language", language.getShortName(), "difficultyLevel",
                difficultyLevel != null ? difficultyLevel.name() : "", "requestedSubtype", requestedSubtype != null ? requestedSubtype.name() : "", "promptHint",
                promptHint != null ? promptHint : "");
    }
}
