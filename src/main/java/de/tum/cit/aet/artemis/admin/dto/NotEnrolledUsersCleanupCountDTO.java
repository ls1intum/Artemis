package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO previewing the not-enrolled-user cleanup. A candidate is eligible only if no business-domain reference remains.
 *
 * @param users        number of eligible users
 * @param blockedUsers number of candidates blocked by remaining references
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(requiredProperties = { "users", "blockedUsers" })
public record NotEnrolledUsersCleanupCountDTO(int users, int blockedUsers) {

    public NotEnrolledUsersCleanupCountDTO(int users) {
        this(users, 0);
    }
}
