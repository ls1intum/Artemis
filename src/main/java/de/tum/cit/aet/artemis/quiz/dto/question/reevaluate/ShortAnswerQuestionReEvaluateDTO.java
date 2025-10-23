package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionReEvaluateDTO(@NotNull Long id, @NotBlank String title, @NotNull String text, @NotNull ScoringType scoringType, @NotNull Boolean randomizeOrder,
        @NotNull Boolean invalid, @NotNull Integer similarityValue, @NotNull Boolean matchLetterCase, @NotEmpty List<@Valid ShortAnswerSpotReEvaluateDTO> spots,
        @NotEmpty List<@Valid ShortAnswerSolutionReEvaluateDTO> solutions, @NotEmpty List<@Valid ShortAnswerMappingReEvaluateDTO> correctMappings)
        implements QuizQuestionReEvaluateDTO {

    public static ShortAnswerQuestionReEvaluateDTO of(ShortAnswerQuestion shortAnswerQuestion) {
        return new ShortAnswerQuestionReEvaluateDTO(shortAnswerQuestion.getId(), shortAnswerQuestion.getTitle(), shortAnswerQuestion.getText(),
                shortAnswerQuestion.getScoringType(), shortAnswerQuestion.isRandomizeOrder(), shortAnswerQuestion.isInvalid(), shortAnswerQuestion.getSimilarityValue(),
                shortAnswerQuestion.getMatchLetterCase(), shortAnswerQuestion.getSpots().stream().map(ShortAnswerSpotReEvaluateDTO::of).toList(),
                shortAnswerQuestion.getSolutions().stream().map(ShortAnswerSolutionReEvaluateDTO::of).toList(),
                shortAnswerQuestion.getCorrectMappings().stream().map(ShortAnswerMappingReEvaluateDTO::of).toList());
    }
}
