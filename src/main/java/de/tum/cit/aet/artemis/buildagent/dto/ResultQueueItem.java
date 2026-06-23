package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultQueueItem(BuildResult buildResult, BuildJobQueueItem buildJobQueueItem, List<BuildLogDTO> buildLogs, Throwable exception) implements Serializable {

    public ResultQueueItem {
        buildLogs = Objects.requireNonNullElse(buildLogs, new ArrayList<>());
    }
}
