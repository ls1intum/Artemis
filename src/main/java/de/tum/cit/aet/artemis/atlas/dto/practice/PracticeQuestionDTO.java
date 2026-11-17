package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeQuestionDTO(long id, String title, String text, Double points, String type, List<PracticeAnswerOptionDTO> answerOptions, Boolean singleChoice,
        Long competencyId, String difficulty) {
}
