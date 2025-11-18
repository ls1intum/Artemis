package de.tum.cit.aet.artemis.core.security.jwt;

import jakarta.validation.constraints.NotNull;

public record JwtWithSource(@NotNull String jwt, @NotNull String source) {
}
