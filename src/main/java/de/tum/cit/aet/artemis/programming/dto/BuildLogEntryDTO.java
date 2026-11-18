package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * A single build log line for a submission.
 * <p>
 * {@code participations/{participationId}/buildlogs} is a SCORPIO route (additive-only): {@code id} is kept even
 * though no in-repo reader dereferences it, because the out-of-repo IntelliJ plugin may. Do not reuse
 * {@code buildagent.dto.BuildLogDTO} here — that record is the Hazelcast build-agent contract, not the REST wire
 * shape, and coupling the two would let an unrelated change ripple into this endpoint.
 *
 * @param id   the build log entry id
 * @param time when the log line was produced
 * @param log  the log line content
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildLogEntryDTO(Long id, ZonedDateTime time, String log) implements Serializable {

    /**
     * Converts a {@link BuildLogEntry} into a {@link BuildLogEntryDTO}.
     *
     * @param buildLogEntry the build log entry to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static BuildLogEntryDTO of(BuildLogEntry buildLogEntry) {
        if (buildLogEntry == null) {
            return null;
        }
        return new BuildLogEntryDTO(buildLogEntry.getId(), buildLogEntry.getTime(), buildLogEntry.getLog());
    }
}
