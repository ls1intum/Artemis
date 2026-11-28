package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a member of an organization with minimal user information
 *
 * @param id                        the user id
 * @param login                     the login
 * @param name                      the full name
 * @param email                     the email address
 * @param visibleRegistrationNumber the registration number as stored, or empty if the member has none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationMemberDTO(Long id, String login, String name, String email, String visibleRegistrationNumber) {
}
