package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * A DTO representing a single row in a course's paginated member list (students/tutors/editors/instructors).
 *
 * @param visibleRegistrationNumber the member's registration number as stored, or empty if the member has none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRoleMemberDTO(Long id, String login, String name, String email, String visibleRegistrationNumber, String imageUrl) {

    /**
     * Creates the member row for the given user.
     *
     * @param user the user to convert
     * @return the member row
     */
    public static CourseRoleMemberDTO of(User user) {
        return new CourseRoleMemberDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(), user.getRegistrationNumber(), user.getImageUrl());
    }
}
