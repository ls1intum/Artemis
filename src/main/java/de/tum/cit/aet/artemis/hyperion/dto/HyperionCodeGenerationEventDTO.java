package de.tum.cit.aet.artemis.hyperion.dto;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

public record HyperionCodeGenerationEventDTO(Type type, String jobId, long exerciseId, Integer iteration, RepositoryType repositoryType, String path, Boolean success,
        Integer attempts, String message) {

    public enum Type {
        STARTED, PROGRESS, FILE_UPDATED, NEW_FILE, DONE, ERROR
    }
}
