package de.tum.cit.aet.artemis.account.dto;

import jakarta.validation.constraints.NotBlank;

public record UserDeletionConfirmationDTO(@NotBlank String login, @NotBlank String impactFingerprint) {
}
