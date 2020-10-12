package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.HashMap;
import java.util.Map;

public class BambooBuildPlanDTO {

    private String name;

    private String description;

    private String projectKey;

    private String projectName;

    private String shortName;

    private String key;

    private String shortKey;

    private boolean enabled;

    private boolean active;

    private boolean building;

    private Map<String, BambooRepositoryDTO> repositories = new HashMap<>();

    public BambooBuildPlanDTO() {
    }

    public BambooBuildPlanDTO(String key) {
        this.key = key;
    }

    public BambooBuildPlanDTO(boolean active, boolean building) {
        this.active = active;
        this.building = building;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isBuilding() {
        return building;
    }

    public void setBuilding(boolean building) {
        this.building = building;
    }

    public BambooRepositoryDTO getRepository(String name) {
        return repositories.get(name);
    }

    public void addRepository(String name, BambooRepositoryDTO repository) {
        repositories.put(name, repository);
    }
}
