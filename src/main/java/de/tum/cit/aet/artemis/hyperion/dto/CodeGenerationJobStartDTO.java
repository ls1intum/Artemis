package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationJobStartDTO(String jobId, RepositoryType repositoryType) {
}
