package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.dto.DragItemDTO;
import de.tum.cit.aet.artemis.quiz.dto.DropLocationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionWithoutSolutionDTO(String backgroundFilePath, List<DropLocationDTO> dropLocations, List<DragItemDTO> dragItems) {

    /**
     * @param dragAndDropQuestion the question to project
     * @return the client-facing view of the question, with the background image named by the path it is served under
     */
    public static DragAndDropQuestionWithoutSolutionDTO of(DragAndDropQuestion dragAndDropQuestion) {
        return new DragAndDropQuestionWithoutSolutionDTO(dragAndDropQuestion.servedBackgroundFilePath(),
                dragAndDropQuestion.getDropLocations().stream().map(DropLocationDTO::of).toList(), dragAndDropQuestion.getDragItems().stream().map(DragItemDTO::of).toList());
    }

}
