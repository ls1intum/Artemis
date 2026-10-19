package de.tum.cit.aet.artemis.admin.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The most recent submission of a single user, used to derive the rolling active user windows.
 *
 * @param userId             the id of the student who submitted
 * @param lastSubmissionDate the date of that student's most recent submission inside the queried window
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveUserLastSubmissionDTO(long userId, ZonedDateTime lastSubmissionDate) {
}
