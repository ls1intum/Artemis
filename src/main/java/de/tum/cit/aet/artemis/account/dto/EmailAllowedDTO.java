package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether an email address is allowed by the restriction that is configured for registration and for changing the address.
 *
 * @param allowed true if the address has valid email syntax and satisfies any configured restriction
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EmailAllowedDTO(boolean allowed) {
}
