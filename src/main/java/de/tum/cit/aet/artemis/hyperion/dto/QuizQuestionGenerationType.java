package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported quiz question types for AI generation.
 */
public enum QuizQuestionGenerationType {

    SINGLE_CHOICE("single-choice"), MULTIPLE_CHOICE("multiple-choice"), TRUE_FALSE("true-false");

    private final String value;

    QuizQuestionGenerationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolves a question type enum from its API value.
     *
     * @param value serialized question type value
     * @return matching question type enum
     */
    @JsonCreator
    public static QuizQuestionGenerationType fromValue(String value) {
        for (QuizQuestionGenerationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown quiz question generation type: " + value);
    }
}
