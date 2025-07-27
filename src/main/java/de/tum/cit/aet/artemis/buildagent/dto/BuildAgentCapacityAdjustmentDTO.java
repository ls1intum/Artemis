package de.tum.cit.aet.artemis.buildagent.dto;

import jakarta.validation.constraints.NotNull;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes./
public class BuildAgentCapacityAdjustmentDTO {

    private final String buildAgentName;

    private final int newCapacity;

    public BuildAgentCapacityAdjustmentDTO(@NotNull String buildAgentName, int newCapacity) {
        if (newCapacity <= 0)
            throw new IllegalArgumentException("New capacity must be greater than 0");
        this.buildAgentName = buildAgentName;
        this.newCapacity = newCapacity;
    }

    public String getBuildAgentName() {
        return buildAgentName;
    }

    public int getNewCapacity() {
        return newCapacity;
    }
}
