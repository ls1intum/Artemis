package de.tum.cit.aet.artemis.notification.dto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * DTO carrying the user fields needed to send a mail and to render mail templates.
 * <p>
 * Used as the {@code user} context variable in Thymeleaf templates rendered by the
 * {@link de.tum.cit.aet.artemis.notification.service.notifications.MailService}, so its
 * accessor names must stay aligned with the property paths used in those templates
 * (e.g. {@code user.login}, {@code user.activationKey}, {@code user.getName()}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MailRecipientDTO(String email, String langKey, String login, String firstName, String lastName, String activationKey, String resetKeyId,
        @Nullable @JsonIgnore String resetKeySecret) {

    public MailRecipientDTO(String email, String langKey, String login, String firstName, String lastName) {
        this(email, langKey, login, firstName, lastName, null, null, null);
    }

    /**
     * Returns the user's full name in the format used by the mail templates.
     */
    public String getName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    public static MailRecipientDTO forUnnamed(String email, String langKey, String login) {
        return new MailRecipientDTO(email, langKey, login, null, null, null, null, null);
    }

    public static MailRecipientDTO forAdministrator(String email, String login) {
        return new MailRecipientDTO(email, "en", login, "Administrator", null, null, null, null);
    }

    public static MailRecipientDTO from(User user) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getActivationKey(), user.getResetKeyId(),
                null);
    }

    public static MailRecipientDTO withResetSecretFrom(String resetKeySecret, User user) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getActivationKey(), user.getResetKeyId(),
                resetKeySecret);
    }

    @Override
    public @NonNull String toString() {
        return "MailRecipientDTO[" + "email='" + email + '\'' + ", langKey='" + langKey + '\'' + ", login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='"
                + lastName + '\'' + ", activationKey='" + activationKey + '\'' + ", resetKeyId='" + resetKeyId + '\'' + ", resetKeySecret=***]";
    }
}
