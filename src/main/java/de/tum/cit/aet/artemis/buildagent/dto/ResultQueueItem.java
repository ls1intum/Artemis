package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.buildagent.domain.BuildLogEntry;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// TODO: this data structure should not use BuildLogEntry because it's an entity class (and not a DTO)
public record ResultQueueItem(BuildResult buildResult, BuildJobQueueItem buildJobQueueItem, List<BuildLogEntry> buildLogs, Throwable exception) implements Serializable {
}
