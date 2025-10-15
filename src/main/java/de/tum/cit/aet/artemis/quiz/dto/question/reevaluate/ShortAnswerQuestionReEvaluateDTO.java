package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionReEvaluateDTO(@NotNull Long id, @NotBlank String title, @NotNull String text, @NotNull ScoringType scoringType, @NotNull Boolean randomizeOrder,
        @NotNull Boolean invalid, @NotNull Integer similarityValue, @NotNull Boolean matchLetterCase, @NotEmpty List<@Valid ShortAnswerSpotReEvaluateDTO> spots,
        @NotEmpty List<@Valid ShortAnswerSolutionReEvaluateDTO> solutions, @NotEmpty List<@Valid ShortAnswerMappingReEvaluateDTO> correctMappings)
        implements QuizQuestionReEvaluateDTO {
}
