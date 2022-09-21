package de.tum.in.www1.artemis.service.connectors.localgit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalGitProjectDTO(String key, String name, String description, Long id, String url, String link) {

    public LocalGitProjectDTO(String key, String name) {
        this(key, name, null, null, null, null);
    }
}
