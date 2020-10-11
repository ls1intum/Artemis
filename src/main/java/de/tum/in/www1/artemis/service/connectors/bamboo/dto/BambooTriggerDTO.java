package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

public class BambooTriggerDTO {

    private Long id;

    private String name;

    private String description;

    private boolean enabled = true;

    public BambooTriggerDTO() {
    }

    public BambooTriggerDTO(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
