package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.Arrays;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RepositoryInfo other)) {
            return false;
        }
        return Objects.equals(repositoryName, other.repositoryName) && repositoryType == other.repositoryType && triggeredByPushTo == other.triggeredByPushTo
                && Objects.equals(assignmentRepositoryUri, other.assignmentRepositoryUri) && Objects.equals(testRepositoryUri, other.testRepositoryUri)
                && Objects.equals(solutionRepositoryUri, other.solutionRepositoryUri) && Arrays.equals(auxiliaryRepositoryUris, other.auxiliaryRepositoryUris)
                && Arrays.equals(auxiliaryRepositoryCheckoutDirectories, other.auxiliaryRepositoryCheckoutDirectories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryName, repositoryType, triggeredByPushTo, assignmentRepositoryUri, testRepositoryUri, solutionRepositoryUri,
                Arrays.hashCode(auxiliaryRepositoryUris), Arrays.hashCode(auxiliaryRepositoryCheckoutDirectories));
    }

    @Override
    public String toString() {
        return "RepositoryInfo[repositoryName=" + repositoryName + ", repositoryType=" + repositoryType + ", triggeredByPushTo=" + triggeredByPushTo + ", assignmentRepositoryUri="
                + assignmentRepositoryUri + ", testRepositoryUri=" + testRepositoryUri + ", solutionRepositoryUri=" + solutionRepositoryUri + ", auxiliaryRepositoryUris="
                + Arrays.toString(auxiliaryRepositoryUris) + ", auxiliaryRepositoryCheckoutDirectories=" + Arrays.toString(auxiliaryRepositoryCheckoutDirectories) + "]";
    }
}
