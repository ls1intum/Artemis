package de.tum.cit.aet.artemis.core.service.distributed.api;

import java.util.Set;

/**
 * Observed process incarnations for activity cleanup, not proof that external writes stopped.
 *
 * @param ownerNodeIds stable process identities observed by the provider
 */
public record CoordinationSnapshot(Set<String> ownerNodeIds) {

    public CoordinationSnapshot {
        ownerNodeIds = Set.copyOf(ownerNodeIds);
    }
}
