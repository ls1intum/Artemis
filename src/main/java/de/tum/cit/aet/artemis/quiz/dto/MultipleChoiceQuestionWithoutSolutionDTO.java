package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionWithoutSolutionDTO(List<AnswerOptionWithoutSolutionDTO> answerOptions, boolean singleChoice) {

    public static MultipleChoiceQuestionWithoutSolutionDTO of(MultipleChoiceQuestion multipleChoiceQuestion) {
        return new MultipleChoiceQuestionWithoutSolutionDTO(multipleChoiceQuestion.getAnswerOptions().stream().map(AnswerOptionWithoutSolutionDTO::of).toList(),
                multipleChoiceQuestion.isSingleChoice());
    }

}
