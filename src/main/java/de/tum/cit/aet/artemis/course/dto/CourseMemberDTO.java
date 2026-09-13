package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * A privacy-safe course member response.
 *
 * @param id                        the user identifier
 * @param login                     the optional login
 * @param name                      the optional display name
 * @param firstName                 the optional first name
 * @param lastName                  the optional last name
 * @param email                     the optional email address
 * @param visibleRegistrationNumber the optional registration number approved for this response
 * @param imageUrl                  the optional profile image URL
 * @param activated                 whether the account is activated
 * @param internal                  whether the account is managed internally
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseMemberDTO(long id, @Nullable String login, @Nullable String name, @Nullable String firstName, @Nullable String lastName, @Nullable String email,
        @Nullable String visibleRegistrationNumber, @Nullable String imageUrl, boolean activated, boolean internal) {

    /**
     * Maps a user after the existing course-member sanitization has been applied.
     *
     * @param user the sanitized user
     * @return the course member response
     */
    public static CourseMemberDTO of(User user) {
        return new CourseMemberDTO(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getVisibleRegistrationNumber(),
                user.getImageUrl(), user.getActivated(), user.isInternal());
    }
}
