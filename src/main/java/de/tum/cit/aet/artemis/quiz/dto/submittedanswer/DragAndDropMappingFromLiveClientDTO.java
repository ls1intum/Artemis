package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The optional {@code dragItemIndex} / {@code dropLocationIndex} fields are denormalized positional hints
 * the entity-shaped JSON carries; the server preserves them on the persisted mapping so existing consumers
 * (e.g. comparators in tests, downstream tooling) keep seeing the same shape they did before this DTO
 * migration. The IDs remain the source of truth.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DragAndDropMappingFromLiveClientDTO(EntityIdRefDTO dragItem, EntityIdRefDTO dropLocation, Integer dragItemIndex, Integer dropLocationIndex) {
}
