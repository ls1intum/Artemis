package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores necessary information for a GitLab response to list personal access tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitLabPersonalAccessTokenListResponseDTO {

    /**
     * The id of the personal access token.
     */
    @JsonProperty
    private Long id;

    public GitLabPersonalAccessTokenListResponseDTO() {
        // default constructor for Jackson
    }

    public GitLabPersonalAccessTokenListResponseDTO(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
