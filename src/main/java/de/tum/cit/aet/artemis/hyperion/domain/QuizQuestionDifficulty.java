package de.tum.cit.aet.artemis.hyperion.domain;

import java.util.Locale;

/**
 * Difficulty band a pool question is generated at.
 */
public enum QuizQuestionDifficulty {

    EASY(20), MEDIUM(50), HARD(80);

    private final int promptValue;

    QuizQuestionDifficulty(int promptValue) {
        this.promptValue = promptValue;
    }

    /**
     * Returns the value rendered into prompts, on the scale 0 (very easy) to 100 (very hard).
     *
     * @return the prompt-scale difficulty
     */
    public int getPromptValue() {
        return promptValue;
    }

    /**
     * Resolves a difficulty from its case-insensitive name.
     *
     * @param value serialized difficulty value, for example {@code medium}
     * @return the matching difficulty
     * @throws IllegalArgumentException if the value matches no difficulty
     */
    public static QuizQuestionDifficulty fromValue(String value) {
        for (QuizQuestionDifficulty difficulty : values()) {
            if (difficulty.name().equalsIgnoreCase(value.trim())) {
                return difficulty;
            }
        }
        throw new IllegalArgumentException("Unknown quiz question difficulty '" + value + "', expected one of " + String.join(", ", names()));
    }

    private static String[] names() {
        QuizQuestionDifficulty[] values = values();
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].name().toLowerCase(Locale.ROOT);
        }
        return names;
    }
}
