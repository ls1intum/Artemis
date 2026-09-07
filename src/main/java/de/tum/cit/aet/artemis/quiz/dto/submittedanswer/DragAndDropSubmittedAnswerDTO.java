package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.DragAndDropMappingDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropSubmittedAnswerDTO(Set<DragAndDropMappingDTO> mappings, String type) {

    /**
     * @param answer the submitted answer to project
     * @return the client-facing view of the answer, with every drag item picture named by the path it is served under
     */
    public static DragAndDropSubmittedAnswerDTO of(DragAndDropSubmittedAnswer answer) {
        // The picture URL of a drag item is scoped by the question that owns the item, and the submitted answer is what knows which question that is. Reading the id off the
        // association does not initialize it.
        Long questionId = answer.getQuizQuestion() != null ? answer.getQuizQuestion().getId() : null;
        return new DragAndDropSubmittedAnswerDTO(answer.getMappings().stream().map(mapping -> DragAndDropMappingDTO.of(questionId, mapping)).collect(Collectors.toSet()),
                "drag-and-drop");
    }

}
