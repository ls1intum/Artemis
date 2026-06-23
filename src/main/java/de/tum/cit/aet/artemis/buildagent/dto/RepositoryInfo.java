package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfo(String repositoryName, RepositoryType repositoryType, RepositoryType triggeredByPushTo, String assignmentRepositoryUri, String testRepositoryUri,
        String solutionRepositoryUri, String[] auxiliaryRepositoryUris, String[] auxiliaryRepositoryCheckoutDirectories) implements Serializable {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    public RepositoryInfo {
        auxiliaryRepositoryUris = Objects.requireNonNullElse(auxiliaryRepositoryUris, EMPTY_STRING_ARRAY);
        auxiliaryRepositoryCheckoutDirectories = Objects.requireNonNullElse(auxiliaryRepositoryCheckoutDirectories, EMPTY_STRING_ARRAY);
    }
}
