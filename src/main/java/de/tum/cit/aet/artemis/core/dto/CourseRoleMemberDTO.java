package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * A DTO representing a single row in a course's paginated member list (students/tutors/editors/instructors).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRoleMemberDTO(Long id, String login, String name, String email, String visibleRegistrationNumber, String imageUrl) {

    /**
     * Maps a user to this DTO. The caller must have already populated {@link User#getVisibleRegistrationNumber()}
     * if the registration number should be visible in the result (it is transient and empty by default).
     *
     * @param user the user to map
     */
    public CourseRoleMemberDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getEmail(), user.getVisibleRegistrationNumber(), user.getImageUrl());
    }
}
