package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a member of an organization with minimal user information
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationMemberDTO(Long id, String login, String name, String email) {
}
