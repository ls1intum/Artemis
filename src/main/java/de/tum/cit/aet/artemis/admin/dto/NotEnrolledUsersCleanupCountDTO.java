package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO previewing the number of users that would be soft-deleted (and anonymized) by the not-enrolled-user cleanup, i.e.
 * users who are enrolled in no course and have been inactive beyond the configured guard period.
 *
 * @param users the number of affected users
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NotEnrolledUsersCleanupCountDTO(int users) {
}
