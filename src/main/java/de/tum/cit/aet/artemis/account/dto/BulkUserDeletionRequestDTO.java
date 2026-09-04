package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record BulkUserDeletionRequestDTO(@NotEmpty List<@Valid UserDeletionConfirmationDTO> users) {
}
