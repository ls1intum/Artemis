package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BitbucketRepositoryDTO(String id, String name, String slug, BitbucketProjectDTO project, String scmId, String state, String statusMessage, boolean forkable,
        String defaultBranch) {

    public BitbucketRepositoryDTO(String name, String defaultBranch) {
        this(null, name, null, null, null, null, null, false, defaultBranch);
    }
}
