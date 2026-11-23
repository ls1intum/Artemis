package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeQuestionDTO(long id, String title, String text, @Nullable String hint, @Nullable String explanation, Double points, String type,
        List<PracticeAnswerOptionDTO> answerOptions, Boolean singleChoice, Long competencyId, String difficulty) {

    public PracticeQuestionDTO(long id, String title, String text, Double points, String type, List<PracticeAnswerOptionDTO> answerOptions, Boolean singleChoice, Long competencyId,
            String difficulty) {
        this(id, title, text, null, null, points, type, answerOptions, singleChoice, competencyId, difficulty);
    }
}
