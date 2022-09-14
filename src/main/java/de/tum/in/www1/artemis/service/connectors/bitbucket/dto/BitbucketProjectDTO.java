package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BitbucketProjectDTO(String key, String name, String description, Long id, String url, String link) {

    public BitbucketProjectDTO(String key, String name) {
        this(key, name, null, null, null, null);
    }
}
