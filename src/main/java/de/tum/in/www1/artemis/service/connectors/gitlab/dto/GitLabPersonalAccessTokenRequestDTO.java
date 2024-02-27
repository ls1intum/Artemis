package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitLabPersonalAccessTokenRequestDTO {

    @JsonProperty
    private String name;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("scopes")
    private String[] scopes;

    @JsonProperty("expires_at")
    private Date expiresAt;

    public GitLabPersonalAccessTokenRequestDTO() {
        // default constructor for Jackson
    }

    public GitLabPersonalAccessTokenRequestDTO(String name, Long userId, String[] scopes) {
        this.name = name;
        this.userId = userId;
        this.scopes = scopes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String[] getScopes() {
        return scopes;
    }

    public void setScopes(String[] scopes) {
        this.scopes = scopes;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
}
