package de.tum.cit.aet.artemis.communication.domain;

/**
 * Enumeration for default channel types that are automatically created on course creation
 */
public enum DefaultChannelType {

    ANNOUNCEMENT("announcement"), ORGANIZATION("organization"), RANDOM("random"), TECH_SUPPORT("tech-support"),;

    private final String name;

    DefaultChannelType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
