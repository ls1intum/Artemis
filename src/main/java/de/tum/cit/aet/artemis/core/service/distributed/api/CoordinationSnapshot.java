package de.tum.cit.aet.artemis.core.service.distributed.api;

import java.util.Set;

/**
 * A complete view of processes that can own coordinated work, not proof that an absent process has stopped external writes.
 *
 * @param ownerNodeIds         stable process-incarnation identities observed by the provider
 * @param memberQuorumRequired whether these processes also host the distributed data and require a configured member quorum
 */
public record CoordinationSnapshot(Set<String> ownerNodeIds, boolean memberQuorumRequired) {

    public CoordinationSnapshot {
        ownerNodeIds = Set.copyOf(ownerNodeIds);
    }

    /**
     * @param expectedMembers configured number of data members, used only by member-hosted stores
     * @return whether the complete configured store is visible for admission
     */
    public boolean permitsAdmission(int expectedMembers) {
        return expectedMembers > 0 && !ownerNodeIds.isEmpty() && (!memberQuorumRequired || ownerNodeIds.size() == expectedMembers);
    }

    /**
     * @param expectedMembers configured number of data members, used only by member-hosted stores
     * @return whether the store can safely inspect an exact-token recovery request; external writes still require operator reconciliation
     */
    public boolean permitsRecovery(int expectedMembers) {
        return expectedMembers > 0 && !ownerNodeIds.isEmpty() && (!memberQuorumRequired || ownerNodeIds.size() > expectedMembers / 2 && ownerNodeIds.size() <= expectedMembers);
    }
}
