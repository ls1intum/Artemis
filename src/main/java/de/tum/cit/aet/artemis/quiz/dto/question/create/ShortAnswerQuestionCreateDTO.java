package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionCreateDTO(@NotEmpty String title, String text, String hint, String explanation, double points, ScoringType scoringType, boolean randomizeOrder,
        List<ShortAnswerSpotCreateDTO> spots, List<ShortAnswerSolutionCreateDTO> solutions, List<ShortAnswerMappingCreateDTO> correctMappings, int similarityValue,
        boolean matchLetterCase) implements QuizQuestionCreateDTO {
}
