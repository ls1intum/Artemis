package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCIBuildPlanDTO(String name, String description, String projectKey, String projectName, String shortName, String key, String shortKey, boolean enabled,
        boolean isActive, boolean isBuilding, Map<String, LocalCIRepositoryDTO> repositories) {

    public LocalCIBuildPlanDTO(String key) {
        this(null, null, null, null, null, key, null, false, false, false, new HashMap<>());
    }

    public LocalCIBuildPlanDTO(boolean isActive, boolean isBuilding) {
        this(null, null, null, null, null, null, null, false, isActive, isBuilding, new HashMap<>());
    }
}
