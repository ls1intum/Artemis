package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The request to create a personal access token.
 *
 * @param name          a label the user recognises the token by
 * @param scopes        the scope names the token is limited to, for example {@code competencies:write}
 * @param expiresInDays the lifetime in days; the configured maximum if omitted
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreatePersonalAccessTokenDTO(@NotBlank @Size(max = 100) String name, @NotEmpty List<@NotBlank String> scopes, @Nullable @Positive Integer expiresInDays) {
}
