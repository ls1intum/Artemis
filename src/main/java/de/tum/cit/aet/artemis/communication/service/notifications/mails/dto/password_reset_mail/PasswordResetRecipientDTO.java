package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.password_reset_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the password reset mail recipient
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PasswordResetRecipientDTO(String langKey, String email, String login, String resetKey) implements IMailRecipientUserDTO {

    /**
     * Factory method to create a {@link PasswordResetRecipientDTO} instance from a {@link User}.
     *
     * @param user The user object used to populate the DTO.
     *                 The {@code resetKey} field in the {@code User}.
     * @return A new {@link PasswordResetRecipientDTO} containing the user's language key, email, login, and reset key.
     * @throws IllegalStateException if the {@code resetKey} field of {@code user} is {@code null}.
     */
    public static PasswordResetRecipientDTO of(User user) {
        if (user.getResetKey() == null) {
            throw new IllegalStateException("Reset key is required");
        }
        return new PasswordResetRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getResetKey());
    }
}
