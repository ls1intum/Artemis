package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BambooBuildPlanDTO(String name, String description, String projectKey, String projectName, String shortName, String key, String shortKey, boolean enabled,
        boolean isActive, boolean isBuilding, Map<String, BambooRepositoryDTO> repositories) {

    public BambooBuildPlanDTO(String key) {
        this(null, null, null, null, null, key, null, false, false, false, new HashMap<>());
    }

    public BambooBuildPlanDTO(boolean isActive, boolean isBuilding) {
        this(null, null, null, null, null, null, null, false, isActive, isBuilding, new HashMap<>());
    }
}
