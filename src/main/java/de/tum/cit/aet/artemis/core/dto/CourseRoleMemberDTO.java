package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a single row in a course's paginated member list (students/tutors/editors/instructors).
 *
 * @param visibleRegistrationNumber the registration number if it should be visible to the caller, empty otherwise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRoleMemberDTO(Long id, String login, String name, String email, String visibleRegistrationNumber, String imageUrl) {
}
