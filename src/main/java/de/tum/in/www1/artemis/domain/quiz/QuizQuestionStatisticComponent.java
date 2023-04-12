package de.tum.in.www1.artemis.domain.quiz;

public interface QuizQuestionStatisticComponent<T1 extends QuizQuestionStatistic, T2 extends QuizQuestionComponent<T3>, T3 extends QuizQuestion> {

    /**
     * @return the id of the implementor.
     */
    Long getId();

    /**
     * Set the QuizQuestionStatistic to the given quizQuestionStatistic
     *
     * @param quizQuestionStatistic the given QuizQuestionStatistic to be set to
     */
    void setQuizQuestionStatistic(T1 quizQuestionStatistic);

    /**
     * @return the QuizQuestionComponent that belongs to the implementor
     */
    T2 getQuizQuestionComponent();
}
