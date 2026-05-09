package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Only the dragItem / dropLocation IDs are bound from the request. The denormalized positional hints
 * {@code dragItemIndex} / {@code dropLocationIndex} that the entity-shaped JSON also carries are silently
 * absorbed by {@link JsonIgnoreProperties} and recomputed server-side from the resolved entities, so the
 * client cannot push indices that disagree with the actual list positions of the validated items.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DragAndDropMappingFromLiveClientDTO(EntityIdRefDTO dragItem, EntityIdRefDTO dropLocation) {
}
