package de.tum.cit.aet.artemis.programming.service.aeolus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the metadata of a {@link Windfile}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WindfileMetadata(String name, String id, String description, String author, String gitCredentials, DockerConfig docker, String resultHook,
        String resultHookCredentials) {

    public WindfileMetadata() {
        this(null, null, null, null, null, null, null, null);
    }
}
