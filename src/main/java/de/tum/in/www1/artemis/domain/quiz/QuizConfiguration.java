package de.tum.in.www1.artemis.domain.quiz;

import java.util.Collection;
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
     * Recreate missing pointers from children to parents that were removed by @JSONIgnore
     */
    default void reconnectJSONIgnoreAttributes() {// iterate through quizQuestions to add missing pointer back to quizExercise
        // Note: This is necessary because of the @IgnoreJSON in question and answerOption
        // that prevents infinite recursive JSON serialization.
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (quizQuestion.getId() != null) {
                setQuestionParent(quizQuestion);
                // reconnect QuestionStatistics
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    setQuizQuestion(quizQuestion.getQuizQuestionStatistic(), quizQuestion);
                }
                // do the same for answerOptions (if quizQuestion is multiple choice)
                if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                    MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic) mcQuestion.getQuizQuestionStatistic();
                    // reconnect answerCounters
                    setQuizQuestionStatistics(mcStatistic.getAnswerCounters(), mcQuestion, mcStatistic);
                    // reconnect answerOptions
                    setQuizQuestions(mcQuestion.getAnswerOptions(), mcQuestion);
                }
                if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                    DragAndDropQuestionStatistic dragAndDropStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
                    // reconnect dropLocations
                    setQuizQuestions(dragAndDropQuestion.getDropLocations(), dragAndDropQuestion);
                    // reconnect dragItems
                    setQuizQuestions(dragAndDropQuestion.getDragItems(), dragAndDropQuestion);
                    // reconnect correctMappings
                    setQuizQuestions(dragAndDropQuestion.getCorrectMappings(), dragAndDropQuestion);
                    // reconnect dropLocationCounters
                    setQuizQuestionStatistics(dragAndDropStatistic.getDropLocationCounters(), dragAndDropQuestion, dragAndDropStatistic);
                }
                if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                    ShortAnswerQuestionStatistic shortAnswerStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
                    // reconnect spots
                    setQuizQuestions(shortAnswerQuestion.getSpots(), shortAnswerQuestion);
                    // reconnect solutions
                    setQuizQuestions(shortAnswerQuestion.getSolutions(), shortAnswerQuestion);
                    // reconnect correctMappings
                    setQuizQuestions(shortAnswerQuestion.getCorrectMappings(), shortAnswerQuestion);
                    // reconnect spotCounters
                    setQuizQuestionStatistics(shortAnswerStatistic.getShortAnswerSpotCounters(), shortAnswerQuestion, shortAnswerStatistic);
                }
            }
        }
    }

    default <T1 extends QuizQuestionComponent<T2>, T2 extends QuizQuestion> void setQuizQuestions(Collection<T1> components, T2 quizQuestion) {
        for (QuizQuestionComponent<T2> mapping : components) {
            setQuizQuestion(mapping, quizQuestion);
        }
    }

    default <T1 extends QuizQuestionStatisticComponent<T2, T3, T4>, T2 extends QuizQuestionStatistic, T3 extends QuizQuestionComponent<T4>, T4 extends QuizQuestion> void setQuizQuestionStatistics(
            Collection<T1> statisticComponents, T4 quizQuestion, T2 quizQuestionStatistic) {
        for (T1 statisticComponent : statisticComponents) {
            if (statisticComponent.getId() != null) {
                statisticComponent.setQuizQuestionStatistic(quizQuestionStatistic);
                setQuizQuestion(statisticComponent.getQuizQuestionComponent(), quizQuestion);
            }
        }
    }

    default <T extends QuizQuestion> void setQuizQuestion(QuizQuestionComponent<T> component, T quizQuestion) {
        if (component != null && component.getId() != null) {
            component.setQuestion(quizQuestion);
        }
    }
}
