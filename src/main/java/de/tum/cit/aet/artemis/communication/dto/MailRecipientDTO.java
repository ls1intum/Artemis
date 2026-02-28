package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO containing only the user fields needed for sending emails.
 * Used by {@link de.tum.cit.aet.artemis.communication.service.notifications.MailService}
 * to avoid depending on the JPA entity directly.
 */
public record MailRecipientDTO(String login, String email, String langKey, String firstName, String lastName, String activationKey, String resetKey) {

    /**
     * Returns the full name of the recipient (first name + last name).
     * This method is accessed by Thymeleaf email templates via {@code ${user.getName()}}.
     *
     * @return the full name
     */
    public String getName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    /**
     * Creates a MailRecipientDTO from a User entity.
     *
     * @param user the user entity
     * @return the DTO containing only the fields needed for email
     */
    public static MailRecipientDTO of(User user) {
        return new MailRecipientDTO(user.getLogin(), user.getEmail(), user.getLangKey(), user.getFirstName(), user.getLastName(), user.getActivationKey(), user.getResetKey());
    }
}
