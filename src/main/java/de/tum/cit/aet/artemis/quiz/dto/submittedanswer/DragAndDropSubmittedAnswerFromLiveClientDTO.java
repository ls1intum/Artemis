package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DragAndDropSubmittedAnswerFromLiveClientDTO(EntityIdRefDTO quizQuestion, Set<DragAndDropMappingFromLiveClientDTO> mappings)
        implements SubmittedAnswerFromLiveClientDTO {
}
