package de.tum.cit.aet.artemis.iris.domain.session;

/**
 * This enum represents the different types of Iris chats.
 */
public enum IrisChatMode {

    TEXT_EXERCISE("text-exercise-chat"), PROGRAMMING_EXERCISE("programming-exercise-chat"), COURSE("course-chat"), LECTURE("lecture-chat"), TUTOR_SUGGESTION("tutor-suggestion");

    private final String value;

    IrisChatMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static IrisChatMode fromValue(String value) {
        for (IrisChatMode mode : values()) {
            if (mode.getValue().equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown chat mode: " + value);
    }
}
