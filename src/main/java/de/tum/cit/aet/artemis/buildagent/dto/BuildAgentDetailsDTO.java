package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record BuildAgentDetailsDTO(long averageBuildDuration, long successfulBuilds, long failedBuilds, long cancelledBuilds, long timedOutBuild, long totalBuilds,
        @Nullable ZonedDateTime lastBuildDate, @NotNull ZonedDateTime startDate, @Nullable String gitRevision, int consecutiveBuildFailures) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
