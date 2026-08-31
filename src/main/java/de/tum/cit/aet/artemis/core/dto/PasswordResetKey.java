package de.tum.cit.aet.artemis.core.dto;

import org.jspecify.annotations.NonNull;

/**
 * Result for a password reset request containing the id of the new reset key as well as the unhashed secret.
 *
 */
public record PasswordResetKey(String id, String secret) {

    @Override
    public @NonNull String toString() {
        return "PasswordResetKey[" + "id='" + id + '\'' + ", secret=***]";
    }
}
