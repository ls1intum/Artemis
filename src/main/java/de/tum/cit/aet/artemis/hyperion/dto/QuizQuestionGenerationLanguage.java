package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported language options for AI quiz question generation.
 */
public enum QuizQuestionGenerationLanguage {

    EN("en"), DE("de");

    private final String value;

    QuizQuestionGenerationLanguage(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolves a language enum from its API value.
     *
     * @param value serialized language value
     * @return matching language enum
     */
    @JsonCreator
    public static QuizQuestionGenerationLanguage fromValue(String value) {
        for (QuizQuestionGenerationLanguage language : values()) {
            if (language.value.equalsIgnoreCase(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown quiz question generation language: " + value);
    }
}
