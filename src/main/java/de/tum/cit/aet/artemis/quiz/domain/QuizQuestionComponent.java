package de.tum.cit.aet.artemis.quiz.domain;

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
