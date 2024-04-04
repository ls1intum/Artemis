package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfo(String repositoryName, RepositoryType repositoryType, RepositoryType triggeredByPushTo, String assignmentRepositoryUri, String testRepositoryUri,
        String solutionRepositoryUri, String[] auxiliaryRepositoryUris, String[] auxiliaryRepositoryCheckoutDirectories) implements Serializable {
}
