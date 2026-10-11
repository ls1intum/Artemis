package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * DTO carrying the recipient fields needed to deliver a course notification across the broadcast channels
 * (web app, email, push). It decouples the broadcast pipeline from the JPA {@code User} entity: the
 * {@code CourseNotificationService} dispatcher converts the recipients once and passes this DTO down to the channels.
 * <p>
 * Used as the {@code recipient} context variable in the course-notification mail templates, so {@link #getName()}
 * must stay aligned with the {@code recipient.getName()} access in those templates.
 * <p>
 * The {@code testUser} flag is not part of the notification payload. The mail channel needs it, because a test user that
 * has not opted in to e-mail must not be sent one, which the mail layer can only decide if the flag reaches it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationRecipientDTO(Long id, String login, String email, String langKey, String firstName, String lastName, @JsonIgnore boolean testUser) {

    /**
     * Returns the recipient's display name in the format used by the mail templates, falling back to the login when the account
     * has no name (see {@link User#displayName}).
     */
    public String getName() {
        return User.displayName(firstName, lastName, login);
    }

    public static CourseNotificationRecipientDTO from(User user) {
        return new CourseNotificationRecipientDTO(user.getId(), user.getLogin(), user.getEmail(), user.getLangKey(), user.getFirstName(), user.getLastName(), user.isTestUser());
    }
}
