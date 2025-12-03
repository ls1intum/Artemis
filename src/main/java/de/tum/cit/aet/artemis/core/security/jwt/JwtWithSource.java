package de.tum.cit.aet.artemis.core.security.jwt;

import org.jspecify.annotations.NonNull;

public record JwtWithSource(@NonNull String jwt, @NonNull String source) {
}
