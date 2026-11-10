package de.tum.cit.aet.artemis.exercise.service;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;

/**
 * The entity that is deleted in the background and where the progress of its deletion is published.
 *
 * @param entityType  the kind of entity that is deleted
 * @param entityId    the id of the entity that is deleted
 * @param destination the websocket destination the progress is sent to
 */
public record DeletionTarget(EntityDeletionType entityType, long entityId, WebsocketDestination destination) {
}
