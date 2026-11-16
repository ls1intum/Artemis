package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;

/**
 * Everything the generation, the regeneration of one question and the rating of a stored answer need to know about a comprehension check: its state, the commit
 * it was generated from, the participation with its repository and effective due date (the individual due date if there is one, else the due date of the
 * exercise), the student, the exercise and the course. A projection of {@code MetisCheckRepository.findGenerationTarget}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisGenerationTargetDTO(long checkId, MetisCheckStatus status, @Nullable String commitHash, long participationId, long studentId, String repositoryUri,
        @Nullable ZonedDateTime effectiveDueDate, long exerciseId, long courseId) {
}
