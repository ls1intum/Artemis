package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabPersonalAccessTokenRequestDTO(String name, @JsonProperty("user_id") Long userId, String[] scopes, @JsonProperty("expires_at") Date expiresAt) {

}
