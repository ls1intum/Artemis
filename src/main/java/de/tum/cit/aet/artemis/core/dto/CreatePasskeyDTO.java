package de.tum.cit.aet.artemis.core.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreatePasskeyDTO(@NotNull String userHandle, @NotEmpty String username, @NotNull String clientDataJSON, @NotNull String attestationObject, Set<String> transports,
        @NotNull String clientExtensions) {
}
