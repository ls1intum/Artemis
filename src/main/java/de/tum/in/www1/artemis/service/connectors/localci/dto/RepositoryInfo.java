package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;

public record RepositoryInfo(String repositoryName, RepositoryType repositoryType, RepositoryType triggeredByPushTo, String assignmentRepositoryUri, String testRepositoryUri,
        String solutionRepositoryUri, String[] auxiliaryRepositoryUris, String[] auxiliaryRepositoryCheckoutDirectories) implements Serializable {
}
