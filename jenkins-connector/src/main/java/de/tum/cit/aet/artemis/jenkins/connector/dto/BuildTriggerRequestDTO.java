package de.tum.cit.aet.artemis.jenkins.connector.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for triggering builds in the Jenkins connector.
 * Contains all information needed to trigger a build in a stateless manner.
 * The connector will internally manage Jenkins-specific identifiers and state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildTriggerRequestDTO(
    @NotNull Long exerciseId,
    @NotNull Long participationId,
    @NotNull @Valid RepositoryInfoDTO exerciseRepository,
    @Valid RepositoryInfoDTO testRepository,
    @Valid RepositoryInfoDTO solutionRepository,
    List<@Valid RepositoryInfoDTO> auxiliaryRepositories,
    @NotBlank String buildScript,
    String triggeredByPushTo,
    @NotBlank String programmingLanguage,
    Map<String, String> additionalProperties
) {
}