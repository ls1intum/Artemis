package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.CoordinationSnapshot;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/** Applies the store's coordination contract without treating external-store clients as data members. */
final class GenerationClusterTopology {

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private final DistributedDataProvider distributedDataProvider;

    private final int expectedDataMemberCount;

    GenerationClusterTopology(DistributedDataProvider distributedDataProvider, int expectedDataMemberCount) {
        this.distributedDataProvider = distributedDataProvider;
        this.expectedDataMemberCount = expectedDataMemberCount;
    }

    void validateConfiguration() {
        if (expectedDataMemberCount < 1) {
            throw new IllegalArgumentException("jhipster.cache.hazelcast.expected-data-member-count must be at least 1");
        }
    }

    CoordinationSnapshot verifyMajority() {
        CoordinationSnapshot snapshot = snapshot();
        if (!snapshot.permitsRecovery(expectedDataMemberCount)) {
            throw new ServiceUnavailableAlertException("Slot recovery requires a complete store view and a majority of configured data members for a member-hosted store.",
                    ENTITY_NAME, "hyperionDataMemberTopologyMismatch");
        }
        return snapshot;
    }

    private CoordinationSnapshot snapshot() {
        try {
            return distributedDataProvider.getCoordinationSnapshot().orElseThrow();
        }
        catch (RuntimeException unavailable) {
            throw new ServiceUnavailableAlertException("The distributed provider cannot establish a complete coordination view.", ENTITY_NAME,
                    "hyperionDataMemberTopologyUnavailable");
        }
    }

    void verifyAllMembers() {
        CoordinationSnapshot snapshot = snapshot();
        if (!snapshot.permitsAdmission(expectedDataMemberCount) || !snapshot.ownerNodeIds().contains(distributedDataProvider.getLocalNodeId())) {
            throw new ServiceUnavailableAlertException(
                    "Authoring requires a connected owner and a complete configured store view: expected " + expectedDataMemberCount + " data members, observed "
                            + snapshot.ownerNodeIds().size() + " owner processes (member-hosted store: " + snapshot.memberQuorumRequired() + ").",
                    ENTITY_NAME, "hyperionDataMemberTopologyMismatch");
        }
    }
}
