package de.tum.cit.aet.artemis.core.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result for a password reset request containing the id of the new reset key as well as the unhashed secret.
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PasswordResetKeyDTO(String id, String secret) {

    @Override
    public @NonNull String toString() {
        return "PasswordResetKeyDto[" + "id='" + id + '\'' + ", secret=***]";
    }
}
