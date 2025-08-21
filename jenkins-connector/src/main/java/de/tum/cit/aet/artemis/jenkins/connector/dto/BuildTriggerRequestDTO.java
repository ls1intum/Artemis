package de.tum.cit.aet.artemis.jenkins.connector.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for triggering builds in external CI connectors.
 * Contains all information needed to trigger a build in a stateless manner.
 *
 * SHARED DTO - Used by both Artemis core and CI connector microservices
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildTriggerRequestDTO(@NotNull Long exerciseId, @NotNull Long participationId, @NotNull @Valid RepositoryInfoDTO exerciseRepository, @Valid RepositoryInfoDTO testRepository,
        List<@Valid RepositoryInfoDTO> auxiliaryRepositories, @NotBlank String buildScript, String scriptType, @NotBlank String programmingLanguage, Map<String, String> additionalProperties) {

    public enum ScriptType {
        SHELL("shell"),
        GROOVY("groovy");
        
        private final String value;
        
        ScriptType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Gets the script type, defaulting to SHELL if not specified.
     */
    public ScriptType getScriptType() {
        if (scriptType == null || scriptType.isBlank()) {
            return ScriptType.SHELL;
        }
        try {
            return ScriptType.valueOf(scriptType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ScriptType.SHELL;
        }
    }
}