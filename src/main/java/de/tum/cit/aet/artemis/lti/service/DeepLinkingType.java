package de.tum.cit.aet.artemis.lti.service;

public enum DeepLinkingType {

    EXERCISE, LECTURE, COMPETENCY, LEARNING_PATH, IRIS;

    /**
     * Get the enum value from a string.
     *
     * @param type The string representation of the deep linking type.
     * @return The corresponding DeepLinkingType.
     * @throws IllegalArgumentException if the type does not match any enum value.
     */
    public static DeepLinkingType fromString(String type) {
        return DeepLinkingType.valueOf(type.toUpperCase());
    }
}
