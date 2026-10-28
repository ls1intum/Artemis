package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A graded participation of the requesting student in an exercise, with its repository, its effective due date (the individual due date if there is one, else
 * the due date of the exercise) and the id of its comprehension check, if one exists. A projection of {@code MetisCheckRepository.findOwnGradedParticipations};
 * no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisOwnParticipationDTO(long participationId, long studentId, @Nullable String repositoryUri, @Nullable ZonedDateTime effectiveDueDate, @Nullable Long checkId) {
}
