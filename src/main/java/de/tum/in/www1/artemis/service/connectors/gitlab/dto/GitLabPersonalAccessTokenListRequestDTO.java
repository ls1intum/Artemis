package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores necessary information for a GitLab request to list personal access tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitLabPersonalAccessTokenListRequestDTO {

    /**
     * Limits the result to personal access tokens with names containing this search string. May be {@code null}.
     */
    @JsonProperty
    private String search;

    /**
     * Limits the result to personal access tokens that are owned by a specific user. May be {@code null}.
     */
    @JsonProperty("user_id")
    private Long userId;

    public GitLabPersonalAccessTokenListRequestDTO() {
        // default constructor for Jackson
    }

    public GitLabPersonalAccessTokenListRequestDTO(String search, Long userId) {
        this.search = search;
        this.userId = userId;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
