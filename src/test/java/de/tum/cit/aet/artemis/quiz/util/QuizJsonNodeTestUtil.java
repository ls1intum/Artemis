package de.tum.cit.aet.artemis.quiz.util;

import com.fasterxml.jackson.databind.JsonNode;

public final class QuizJsonNodeTestUtil {

    private QuizJsonNodeTestUtil() {
    }

    /**
     * Finds a quiz question with the requested type in a serialized question array.
     *
     * @param questions the serialized quiz questions
     * @param type      the requested question type
     * @return the matching question
     * @throws AssertionError if no matching question exists
     */
    public static JsonNode findQuestionByType(JsonNode questions, String type) {
        for (JsonNode question : questions) {
            if (type.equals(question.path("type").asText())) {
                return question;
            }
        }
        throw new AssertionError("Missing quiz question of type " + type);
    }
}
