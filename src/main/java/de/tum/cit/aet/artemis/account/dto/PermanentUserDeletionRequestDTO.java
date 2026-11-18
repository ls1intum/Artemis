package de.tum.cit.aet.artemis.account.dto;

import jakarta.validation.constraints.NotBlank;

public record PermanentUserDeletionRequestDTO(@NotBlank String impactFingerprint) {
}
