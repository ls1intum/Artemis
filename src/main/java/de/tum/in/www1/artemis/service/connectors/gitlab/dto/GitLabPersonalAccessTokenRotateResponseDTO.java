package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GitLabPersonalAccessTokenRotateResponseDTO extends GitLabPersonalAccessTokenRotateRequestDTO {

    @JsonProperty
    private String token;

    public GitLabPersonalAccessTokenRotateResponseDTO() {
        // default constructor for Jackson
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
