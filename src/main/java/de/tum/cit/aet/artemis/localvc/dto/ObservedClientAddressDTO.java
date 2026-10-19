package de.tum.cit.aet.artemis.localvc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The address a core node resolves a caller to.
 *
 * @param address the caller's address as the node sees it, resolved exactly as the git request paths resolve it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ObservedClientAddressDTO(String address) {
}
