package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The number of submissions of one participant in a set of exercises, an intermediate result of the public usage statistics. It never leaves the server.
 *
 * @param studentId   the id of the student, null for submissions of team participations
 * @param submissions the number of submissions
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentSubmissionCountDTO(Long studentId, long submissions) {
}
