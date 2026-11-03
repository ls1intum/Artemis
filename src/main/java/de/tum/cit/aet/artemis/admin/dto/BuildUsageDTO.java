package de.tum.cit.aet.artemis.admin.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated, anonymous build numbers of one semester.
 *
 * @param count                  number of finished build jobs (successful, failed, errored or timed out) of the exercises of the counted courses
 * @param averageDurationSeconds average time in seconds from start to completion of the successful build jobs, absent without such jobs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildUsageDTO(long count, Double averageDurationSeconds) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
