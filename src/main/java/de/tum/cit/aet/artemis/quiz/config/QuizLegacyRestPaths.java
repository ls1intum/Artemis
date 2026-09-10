package de.tum.cit.aet.artemis.quiz.config;

/**
 * Centralised legacy REST paths for the quiz module.
 */
public final class QuizLegacyRestPaths {

    /**
     * Legacy endpoint that recalculated persisted quiz statistics. Its successor calculates the point distribution on demand.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String RECALCULATE_STATISTICS = "quiz-exercises/{quizExerciseId}/recalculate-statistics";

    private QuizLegacyRestPaths() {
        // utility class
    }
}
