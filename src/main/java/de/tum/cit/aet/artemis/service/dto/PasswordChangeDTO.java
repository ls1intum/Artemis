package de.tum.cit.aet.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a password change required data - current and new password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PasswordChangeDTO(String currentPassword, String newPassword) {
}
