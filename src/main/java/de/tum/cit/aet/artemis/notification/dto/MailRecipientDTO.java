package de.tum.cit.aet.artemis.notification.dto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasswordResetKey;

/**
 * DTO carrying the user fields needed to send a mail and to render mail templates.
 * <p>
 * Used as the {@code user} context variable in Thymeleaf templates rendered by the
 * {@link de.tum.cit.aet.artemis.notification.service.notifications.MailService}, so its
 * accessor names must stay aligned with the property paths used in those templates
 * (e.g. {@code user.login}, {@code user.activationKey}, {@code user.getName()}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MailRecipientDTO(String email, String langKey, String login, String firstName, String lastName, String activationKey, @Nullable PasswordResetKey resetKey) {

    public MailRecipientDTO(String email, String langKey, String login, String firstName, String lastName) {
        this(email, langKey, login, firstName, lastName, null, null);
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
        return new MailRecipientDTO(email, langKey, login, null, null, null, null);
    }

    public static MailRecipientDTO forAdministrator(String email, String login) {
        return new MailRecipientDTO(email, "en", login, "Administrator", null, null, null);
    }

    /**
     * For the mails that need no recovery key, which is all of them except account activation and password reset. The keys
     * live in {@code user_recovery_key}, so they cannot be read from the user.
     *
     * @param user the recipient
     * @return the recipient without any recovery key
     */
    public static MailRecipientDTO from(User user) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), null, null);
    }

    /**
     * For the password-reset mails, whose templates render the key. The caller passes the key it has just
     * issued rather than the DTO reading it back.
     *
     * @param user     the recipient
     * @param resetKey the reset key to render, or null
     * @return the recipient carrying the given key
     */
    public static MailRecipientDTO withResetKeyFrom(User user, PasswordResetKey resetKey) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), null, resetKey);
    }

    /**
     * For the activation mails, whose templates render the key. The caller passes the key it has just
     * issued rather than the DTO reading it back.
     *
     * @param user          the recipient
     * @param activationKey the activation key to render, or null
     * @return the recipient carrying the given key
     */
    public static MailRecipientDTO withActivationKeyFrom(User user, String activationKey) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), activationKey, null);
    }

    @Override
    public @NonNull String toString() {
        return "MailRecipientDTO[" + "email='" + email + '\'' + ", langKey='" + langKey + '\'' + ", login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='"
                + lastName + '\'' + ", activationKey=***, resetKey=" + resetKey + ']';
    }
}
