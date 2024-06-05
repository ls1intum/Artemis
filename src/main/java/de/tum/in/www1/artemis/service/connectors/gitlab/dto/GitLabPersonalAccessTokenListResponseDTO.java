package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores necessary information for a GitLab response to list personal access tokens.
 *
 * @param id The id of the personal access token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabPersonalAccessTokenListResponseDTO(@JsonProperty Long id) {
}
