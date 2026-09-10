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
     * All three question types store their components (answer options; drop locations, drag items, and correct mappings; or spots, solutions, and correct mappings) by id inside
     * {@code quiz_question.content}. Those JSON values no longer have child-to-question back-references to reconnect. The remaining ignored pointer is the question's parent
     * exercise or pool, restored through {@link #setQuestionParent(QuizQuestion)}.
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
        }
    }
}
