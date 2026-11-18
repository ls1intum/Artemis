package de.tum.cit.aet.artemis.admin.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The public usage statistics of the last semesters, newest first.
 *
 * @param generatedAt the time the numbers were computed, they are cached for about one hour
 * @param semesters   one entry per semester, newest first
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PublicUsageStatisticsDTO(Instant generatedAt, List<UsageSemesterDTO> semesters) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
