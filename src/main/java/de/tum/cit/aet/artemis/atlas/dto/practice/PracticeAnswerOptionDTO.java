package de.tum.cit.aet.artemis.atlas.dto.practice;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeAnswerOptionDTO(long id, String text, String hint, Boolean isCorrect, String explanation) {

    public static PracticeAnswerOptionDTO withoutSolution(long id, String text, String hint) {
        return new PracticeAnswerOptionDTO(id, text, hint, null, null);
    }

    public static PracticeAnswerOptionDTO withSolution(long id, String text, String hint, boolean isCorrect, String explanation) {
        return new PracticeAnswerOptionDTO(id, text, hint, isCorrect, explanation);
    }
}
