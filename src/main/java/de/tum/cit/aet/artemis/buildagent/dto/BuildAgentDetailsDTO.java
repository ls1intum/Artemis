package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

public record BuildAgentDetailsDTO(long averageBuildDuration, long successfulBuilds, long failedBuilds, long cancelledBuilds, long timedOutBuild, long totalBuilds,
        ZonedDateTime lastBuildDate, ZonedDateTime startDate, String gitRevision) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
