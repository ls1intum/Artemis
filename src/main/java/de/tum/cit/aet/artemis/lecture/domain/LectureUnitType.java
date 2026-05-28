package de.tum.cit.aet.artemis.lecture.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stable, serializable discriminator for the {@link LectureUnit} subtype.
 * <p>
 * Mirrors the {@code @JsonSubTypes} names declared on {@link LectureUnit} ({@code "attachment"}, {@code "exercise"},
 * {@code "text"}, {@code "online"}) and the {@code LectureUnitType} enum used by the client. Response DTOs expose this
 * instead of a {@code Class<? extends LectureUnit>} so the wire format does not leak internal Java class names.
 */
public enum LectureUnitType {

    ATTACHMENT_VIDEO("attachment"), EXERCISE("exercise"), TEXT("text"), ONLINE("online");

    private final String value;

    LectureUnitType(String value) {
        this.value = value;
    }

    /**
     * @return the stable wire value (matches the {@code @JsonSubTypes} name and the client enum)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Maps a {@link LectureUnit} entity class to its discriminator.
     *
     * @param lectureUnitClass the concrete lecture unit entity class
     * @return the matching {@link LectureUnitType}
     * @throws IllegalArgumentException if the class is not a known lecture unit subtype
     */
    public static LectureUnitType fromEntityClass(Class<? extends LectureUnit> lectureUnitClass) {
        if (AttachmentVideoUnit.class.isAssignableFrom(lectureUnitClass)) {
            return ATTACHMENT_VIDEO;
        }
        if (ExerciseUnit.class.isAssignableFrom(lectureUnitClass)) {
            return EXERCISE;
        }
        if (TextUnit.class.isAssignableFrom(lectureUnitClass)) {
            return TEXT;
        }
        if (OnlineUnit.class.isAssignableFrom(lectureUnitClass)) {
            return ONLINE;
        }
        throw new IllegalArgumentException("Unknown lecture unit type: " + lectureUnitClass.getName());
    }
}
