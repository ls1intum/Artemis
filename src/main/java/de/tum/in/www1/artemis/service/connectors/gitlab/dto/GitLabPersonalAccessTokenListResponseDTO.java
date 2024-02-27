package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitLabPersonalAccessTokenListResponseDTO {

    @JsonProperty
    private Long id;

    @JsonProperty("expires_at")
    private Date expiresAt;

    public GitLabPersonalAccessTokenListResponseDTO() {
        // default constructor for Jackson
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
}
