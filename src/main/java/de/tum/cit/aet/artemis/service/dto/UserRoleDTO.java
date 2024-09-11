package de.tum.cit.aet.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.metis.UserRole;

/**
 * A Data Transfer Object (DTO) representing a user's role within a course.
 *
 * <p>
 * This DTO contains the user ID, login, and role of a user. It is used to encapsulate and transfer
 * the user role information between different layers of the application.
 * </p>
 *
 * <p>
 * The `UserRoleDTO` can be initialized either with a {@link UserRole} enum or a {@link String} that will
 * be converted to the corresponding {@link UserRole}.
 * </p>
 *
 * @param userId   the unique identifier of the user
 * @param username the login of the user
 * @param role     the role of the user within the course, represented as a {@link UserRole} enum
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserRoleDTO(Long userId, String username, UserRole role) {

    @SuppressWarnings("unused")
    UserRoleDTO(Long userId, String username, String role) {
        this(userId, username, role != null ? UserRole.valueOf(role) : null);
    }
}
