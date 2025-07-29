package de.tum.cit.aet.artemis.buildagent.dto;

import jakarta.validation.constraints.NotNull;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes./
public record BuildAgentCapacityAdjustmentDTO(@NotNull String buildAgentName, int newCapacity) {

    public BuildAgentCapacityAdjustmentDTO {
        if (newCapacity <= 0)
            throw new IllegalArgumentException("New capacity must be greater than 0");
    }
}
