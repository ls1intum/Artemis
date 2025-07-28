package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A DTO representing a user with the minimal information allowed to be seen by other users in a course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserPublicInfoDTO(@SuppressWarnings("PMD.ShortVariable") Long id, String login, String name, String firstName, String lastName, String imageUrl, Boolean isInstructor,
        Boolean isEditor, Boolean isTeachingAssistant, Boolean isStudent) {

    public UserPublicInfoDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getImageUrl(), null, null, null, null);
    }

    /**
     * Creates a new UserPublicInfoDTO with role properties assigned based on the given course and user
     *
     * @param course the course to check the roles for
     * @param user   the user to check the roles for
     * @return a new UserPublicInfoDTO with assigned roles
     */
    public UserPublicInfoDTO withRoles(Course course, User user) {
        return new UserPublicInfoDTO(this.id, this.login, this.name, this.firstName, this.lastName, this.imageUrl, user.getGroups().contains(course.getInstructorGroupName()),
                user.getGroups().contains(course.getEditorGroupName()), user.getGroups().contains(course.getTeachingAssistantGroupName()),
                user.getGroups().contains(course.getStudentGroupName()));
    }
}
