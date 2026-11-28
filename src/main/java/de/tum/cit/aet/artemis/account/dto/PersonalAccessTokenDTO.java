package de.tum.cit.aet.artemis.account.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A personal access token as shown in the list of a user's tokens. Never contains the secret.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PersonalAccessTokenDTO(long id, String name, String tokenPrefix, List<String> scopes, Instant createdDate, Instant expiryDate, @Nullable Instant lastUsedDate) {
}
