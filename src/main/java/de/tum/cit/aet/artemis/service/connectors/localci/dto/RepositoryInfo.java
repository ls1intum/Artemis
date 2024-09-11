package de.tum.cit.aet.artemis.service.connectors.localci.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfo(String repositoryName, RepositoryType repositoryType, RepositoryType triggeredByPushTo, String assignmentRepositoryUri, String testRepositoryUri,
        String solutionRepositoryUri, String[] auxiliaryRepositoryUris, String[] auxiliaryRepositoryCheckoutDirectories) implements Serializable {
}
