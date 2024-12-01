package de.tum.cit.aet.artemis.quiz.domain;

public interface QuizQuestionStatisticComponent<S extends QuizQuestionStatistic, C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> {

    /**
     * @return the id of the implementor.
     */
    Long getId();

    /**
     * Set the QuizQuestionStatistic to the given quizQuestionStatistic
     *
     * @param quizQuestionStatistic the given QuizQuestionStatistic to be set to
     */
    void setQuizQuestionStatistic(S quizQuestionStatistic);

    /**
     * @return the QuizQuestionComponent that belongs to the implementor
     */
    C getQuizQuestionComponent();

    /**
     * Set the QuizQuestionComponent of the implementor to the given quizQuestionComponent
     *
     * @param quizQuestionComponent the quizQuestionComponent to be set to
     */
    void setQuizQuestionComponent(C quizQuestionComponent);
}
