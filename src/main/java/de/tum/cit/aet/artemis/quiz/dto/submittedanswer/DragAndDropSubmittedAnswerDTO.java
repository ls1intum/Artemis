package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.DragAndDropMappingDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropSubmittedAnswerDTO(Set<DragAndDropMappingDTO> mappings) {

    public static DragAndDropSubmittedAnswerDTO of(DragAndDropSubmittedAnswer answer) {
        return new DragAndDropSubmittedAnswerDTO(answer.getMappings().stream().map(DragAndDropMappingDTO::of).collect(Collectors.toSet()));
    }

}
