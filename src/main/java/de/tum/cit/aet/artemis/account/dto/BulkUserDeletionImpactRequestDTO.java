package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The logins an administrator wants the combined deletion impact for.
 *
 * @param logins the accounts to preview, at least one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BulkUserDeletionImpactRequestDTO(@NotEmpty List<@NotEmpty String> logins) {
}
