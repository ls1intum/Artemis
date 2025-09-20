package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

// TODO: Check if we need to use indexes here
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingCreateDTO(int dragItemIndex, int dropLocationIndex) {
}
