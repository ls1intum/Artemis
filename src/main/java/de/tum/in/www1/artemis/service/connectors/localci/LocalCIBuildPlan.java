package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCIBuildPlan(String name, String description, String projectKey, String projectName, String shortName, String key, String shortKey, boolean enabled,
        boolean isActive, boolean isBuilding, Map<String, LocalVCRepositoryUrl> repositories) {

    public LocalCIBuildPlan(String key) {
        this(null, null, null, null, null, key, null, false, false, false, new HashMap<>());
    }

    public LocalCIBuildPlan(boolean isActive, boolean isBuilding) {
        this(null, null, null, null, null, null, null, false, isActive, isBuilding, new HashMap<>());
    }
}
