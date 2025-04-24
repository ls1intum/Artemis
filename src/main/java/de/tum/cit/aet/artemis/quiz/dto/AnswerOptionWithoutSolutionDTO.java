package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionWithoutSolutionDTO(Long id, String text, String hint, Boolean invalid) {

    public static AnswerOptionWithoutSolutionDTO of(AnswerOption answerOption) {
        return new AnswerOptionWithoutSolutionDTO(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.isInvalid());
    }

}
