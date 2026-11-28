package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationJobStartDTO(String jobId, @Schema(implementation = String.class, allowableValues = {
        "TEMPLATE", "SOLUTION", "TESTS", "AUXILIARY", "USER" }) RepositoryType repositoryType){
}
