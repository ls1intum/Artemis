package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BambooBuildPlanDTO {

    private String name;

    private String description;

    private String projectKey;

    private String projectName;

    private String shortName;

    private String key;

    private String shortKey;

    private boolean enabled;

    private boolean isActive;

    private boolean isBuilding;

    private final Map<String, BambooRepositoryDTO> repositories = new HashMap<>();

    /**
     * needed for Jackson
     */
    public BambooBuildPlanDTO() {
    }

    public BambooBuildPlanDTO(String key) {
        this.key = key;
    }

    public BambooBuildPlanDTO(boolean isActive, boolean isBuilding) {
        this.isActive = isActive;
        this.isBuilding = isBuilding;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getShortKey() {
        return shortKey;
    }

    public void setShortKey(String shortKey) {
        this.shortKey = shortKey;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(boolean active) {
        this.isActive = active;
    }

    public boolean getIsBuilding() {
        return isBuilding;
    }

    public void setIsBuilding(boolean building) {
        this.isBuilding = building;
    }

    public BambooRepositoryDTO getRepository(String name) {
        return repositories.get(name);
    }

    public void addRepository(String name, BambooRepositoryDTO repository) {
        repositories.put(name, repository);
    }
}
