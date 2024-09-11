package de.tum.cit.aet.artemis.domain.quiz;

public interface QuizQuestionComponent<Q extends QuizQuestion> {

    /**
     * @return the id of the implementor.
     */
    Long getId();

    /**
     * Set the QuizQuestion of the implementor to the given quizQuestion
     *
     * @param quizQuestion the QuizQuestion to be set to
     */
    void setQuestion(Q quizQuestion);
}
