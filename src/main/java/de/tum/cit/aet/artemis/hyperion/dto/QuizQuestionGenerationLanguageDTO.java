package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported language options for AI quiz question generation.
 */
public enum QuizQuestionGenerationLanguageDTO {

    EN("en"), DE("de");

    private final String value;

    QuizQuestionGenerationLanguageDTO(String value) {
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
    public static QuizQuestionGenerationLanguageDTO fromValue(String value) {
        for (QuizQuestionGenerationLanguageDTO language : values()) {
            if (language.value.equalsIgnoreCase(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown quiz question generation language: " + value);
    }
}
