package de.tum.cit.aet.artemis.quiz.domain;

import java.util.List;

public interface QuizConfiguration {

    /**
     * Find the list of QuizQuestion of the implementor entity.
     *
     * @return the list of QuizQuestion belongs to the implementor entity
     */
    List<QuizQuestion> getQuizQuestions();

    /**
     * Set the parent of the given QuizQuestion to reconnect ignored JSON attributes.
     *
     * @param quizQuestion the QuizQuestion of which the parent to be set
     */
    void setQuestionParent(QuizQuestion quizQuestion);

    /**
     * Recreate missing pointers from children to parents that were removed by {@code @JsonIgnore}.
     * <p>
     * Since all three question types now store their components (answer options / drop locations / drag items / correct mappings, resp. spots / solutions / correct mappings) and
     * their statistics counters id-based inside the {@code content} / {@code counters} JSON columns, there are no more {@code @JsonIgnore} child back-references to reconnect
     * except
     * the question's own statistic (a {@code @OneToOne} whose back-reference is {@code @JsonIgnore}d) and the question's parent exercise/pool.
     */
    default void reconnectJSONIgnoreAttributes() {
        if (getQuizQuestions() == null) {
            return;
        }
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (quizQuestion == null) {
                continue;
            }
            setQuestionParent(quizQuestion);
            // reconnect the question statistic's back-reference to its question (statistic is null on transient questions before initializeStatistic())
            QuizQuestionStatistic quizQuestionStatistic = quizQuestion.getQuizQuestionStatistic();
            if (quizQuestionStatistic != null) {
                quizQuestionStatistic.setQuizQuestion(quizQuestion);
            }
        }
    }
}
