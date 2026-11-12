package de.tum.cit.aet.artemis.exercise.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;

/**
 * Identifies the owner of a running asynchronous deletion.
 *
 * @param scopeKey            the key that only one running deletion may hold and under which the status is kept, see {@link EntityDeletionProgressService#exerciseScopeKey(long)}
 * @param additionalScopeKeys further keys the deletion holds, without a status of their own, for example the exercises that an exam deletes
 * @param entityType          the kind of entity that is deleted
 * @param entityId            the id of the entity that is deleted
 * @param destination         the websocket destination the progress is sent to
 * @param totalSteps          the number of steps of the deletion
 * @param startedAt           when the deletion started
 * @param ownerToken          the unique token that owns the distributed claims
 */
public record EntityDeletionClaim(String scopeKey, List<String> additionalScopeKeys, EntityDeletionType entityType, long entityId, WebsocketDestination destination, int totalSteps,
        ZonedDateTime startedAt, UUID ownerToken) {

    /**
     * All keys the deletion holds, the scope of its status first.
     *
     * @return the scope key followed by the additional scope keys
     */
    public List<String> allScopeKeys() {
        List<String> keys = new ArrayList<>(additionalScopeKeys.size() + 1);
        keys.add(scopeKey);
        keys.addAll(additionalScopeKeys);
        return keys;
    }
}
