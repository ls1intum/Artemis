package de.tum.cit.aet.artemis.notification.dto;

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
public record MailRecipientDTO(String email, String langKey, String login, String firstName, String lastName, String activationKey, String resetKey) {

    /**
     * Returns the user's full name in the format used by the mail templates.
     */
    public String getName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName;
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
     * For the activation and password-reset mails, whose templates render the key. The caller passes the key it has just
     * issued rather than the DTO reading it back.
     *
     * @param user          the recipient
     * @param activationKey the activation key to render, or null
     * @param resetKey      the reset key to render, or null
     * @return the recipient carrying the given key
     */
    public static MailRecipientDTO withRecoveryKey(User user, String activationKey, String resetKey) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), activationKey, resetKey);
    }
}
