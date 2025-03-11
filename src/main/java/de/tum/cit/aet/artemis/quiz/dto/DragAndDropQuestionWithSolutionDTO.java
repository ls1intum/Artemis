package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionWithSolutionDTO(@JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO,
        List<DragAndDropMappingDTO> correctMappings) {

    public static DragAndDropQuestionWithSolutionDTO of(DragAndDropQuestion dragAndDropQuestion) {
        return new DragAndDropQuestionWithSolutionDTO(DragAndDropQuestionWithoutSolutionDTO.of(dragAndDropQuestion),
                dragAndDropQuestion.getCorrectMappings().stream().map(DragAndDropMappingDTO::of).toList());
    }

}
