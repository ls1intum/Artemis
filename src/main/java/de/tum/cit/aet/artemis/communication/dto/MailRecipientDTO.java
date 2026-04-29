package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO carrying the user fields needed to send a mail and to render mail templates.
 * <p>
 * Used as the {@code user} context variable in Thymeleaf templates rendered by the
 * {@link de.tum.cit.aet.artemis.communication.service.notifications.MailService}, so its
 * accessor names must stay aligned with the property paths used in those templates
 * (e.g. {@code user.login}, {@code user.activationKey}, {@code user.getName()}).
 */
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

    public static MailRecipientDTO from(User user) {
        return new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getActivationKey(), user.getResetKey());
    }
}
