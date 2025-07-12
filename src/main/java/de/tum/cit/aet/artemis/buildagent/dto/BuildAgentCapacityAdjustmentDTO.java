package de.tum.cit.aet.artemis.buildagent.dto;

/** DTO for build agent capacity adjustment messages. */
public class BuildAgentCapacityAdjustmentDTO {

    private final String buildAgentName;

    private final int newCapacity;

    public BuildAgentCapacityAdjustmentDTO(String buildAgentName, int newCapacity) {
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
