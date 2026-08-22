package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.dto.DragAndDropMappingDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionWithSolutionDTO(@JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO,
        List<DragAndDropMappingDTO> correctMappings) {

    /**
     * Creates a DragAndDropQuestionWithSolutionDTO from the given question.
     *
     * @param dragAndDropQuestion the drag-and-drop question
     * @return the DTO
     */
    public static DragAndDropQuestionWithSolutionDTO of(DragAndDropQuestion dragAndDropQuestion) {
        // correctMappings is null on a question that has been masked for students (solutions/mappings stripped before
        // results are published); treat that as no mappings instead of dereferencing null.
        List<DragAndDropMappingDTO> correctMappings = dragAndDropQuestion.getCorrectMappings() == null ? null
                : dragAndDropQuestion.getCorrectMappings().stream().map(DragAndDropMappingDTO::of).toList();
        return new DragAndDropQuestionWithSolutionDTO(DragAndDropQuestionWithoutSolutionDTO.of(dragAndDropQuestion), correctMappings);
    }

}
