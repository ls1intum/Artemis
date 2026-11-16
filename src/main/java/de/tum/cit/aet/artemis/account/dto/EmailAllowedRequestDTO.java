package de.tum.cit.aet.artemis.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The email address that a form asks about while it is typed.
 *
 * @param email the address as it was entered, at most as long as an address can be stored
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EmailAllowedRequestDTO(@NotNull @Size(max = 100) String email) {
}
