package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for triggering builds in external CI connectors.
 * Contains all information needed to trigger a build in a stateless manner.
 * <p>
 * SHARED DTO - Used by both Artemis core and CI connector microservices
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildTriggerRequestDTO(@NotNull Long exerciseId, @NotNull Long participationId, @NotNull @Valid RepositoryDTO exerciseRepository, @Valid RepositoryDTO testRepository,
        List<@Valid RepositoryDTO> auxiliaryRepositories, @NotBlank String buildScript, ScriptType scriptType, @NotBlank String programmingLanguage,
        Map<String, String> additionalProperties) {

    public enum ScriptType {

        SHELL("shell"), GROOVY("groovy");

        private final String value;

        ScriptType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
