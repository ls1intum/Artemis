package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitLabPersonalAccessTokenListResponseDTO extends GitLabPersonalAccessTokenRequestDTO {

    @JsonProperty
    private Long id;

    public GitLabPersonalAccessTokenListResponseDTO() {
        // default constructor for Jackson
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
