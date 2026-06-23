package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JobTimingInfo(@NonNull ZonedDateTime submissionDate, @Nullable ZonedDateTime buildStartDate, @Nullable ZonedDateTime buildCompletionDate,
        @Nullable ZonedDateTime estimatedCompletionDate, long estimatedDuration) implements Serializable {
}
