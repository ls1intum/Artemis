package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerOptionWithSolutionDTO(@JsonUnwrapped AnswerOptionWithoutSolutionDTO answerOptionWithoutSolutionDTO, String explanation, Boolean isCorrect) {

    public static AnswerOptionWithSolutionDTO of(AnswerOption answerOption) {
        return new AnswerOptionWithSolutionDTO(AnswerOptionWithoutSolutionDTO.of(answerOption), answerOption.getExplanation(), answerOption.isIsCorrect());
    }

}
