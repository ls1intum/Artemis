package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePasskeyDTO(@NotNull @JsonProperty("userHandle") String userHandle, @NotEmpty @JsonProperty("username") String username,
        @NotNull @JsonProperty("webAuthnCredential") WebAuthnCredential webAuthnCredential) {

    @JsonCreator
    public CreatePasskeyDTO {
    }
}
