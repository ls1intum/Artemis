package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitLabPersonalAccessTokenRotateRequestDTO {

    @JsonProperty
    private Long id;

    @JsonProperty("expires_at")
    private LocalDate expiresAt;

    public GitLabPersonalAccessTokenRotateRequestDTO() {
        // default constructor for Jackson
    }

    public GitLabPersonalAccessTokenRotateRequestDTO(Long id, LocalDate expiresAt) {
        this.id = id;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }
}
