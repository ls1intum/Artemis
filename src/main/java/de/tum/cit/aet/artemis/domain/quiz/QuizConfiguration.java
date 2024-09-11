package de.tum.cit.aet.artemis.domain.quiz;

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

    /**
     * Set the QuizQuestion of the given components
     *
     * @param components   the QuizQuestionComponent of which the given quizQuestion to be set to
     * @param quizQuestion the QuizQuestion to be set to
     * @param <C>          the class that implements QuizQuestionComponent
     * @param <Q>          the subclass of QuizQuestion to be set to
     */
    default <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> void setQuizQuestions(Collection<C> components, Q quizQuestion) {
        for (QuizQuestionComponent<Q> mapping : components) {
            setQuizQuestion(mapping, quizQuestion);
        }
    }

    /**
     * Set the QuizQuestionStatistic and the QuizQuestion of the given statisticComponents
     *
     * @param statisticComponents   the QuizQuestionStatisticComponent of which the QuizQuestionStatistic to be set
     * @param quizQuestion          the QuizQuestion to be set to
     * @param quizQuestionStatistic the QuizQuestionStatistic to be set to
     * @param <SC>                  the class that implements QuizQuestionStatisticComponent of which the QuizQuestionStatistic to be set
     * @param <S>                   the subclass that implements QuizQuestionStatistic to be set to
     * @param <C>                   the class that implements QuizQuestionComponent
     * @param <Q>                   the subclass of QuizQuestion to be set to
     */
    default <SC extends QuizQuestionStatisticComponent<S, C, Q>, S extends QuizQuestionStatistic, C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> void setQuizQuestionStatistics(
            Collection<SC> statisticComponents, Q quizQuestion, S quizQuestionStatistic) {
        for (SC statisticComponent : statisticComponents) {
            if (statisticComponent.getId() != null) {
                statisticComponent.setQuizQuestionStatistic(quizQuestionStatistic);
                if (!(quizQuestion instanceof MultipleChoiceQuestion)) {
                    setQuizQuestion(statisticComponent.getQuizQuestionComponent(), quizQuestion);
                }
            }
        }
    }

    /**
     * Set the QuizQuestion of the given component
     *
     * @param component    the QuizQuestionComponent of which the QuizQuestion to be set
     * @param quizQuestion the QuizQuestion to be set to
     * @param <Q>          the subclass of QuizQuestion to be set to
     */
    default <Q extends QuizQuestion> void setQuizQuestion(QuizQuestionComponent<Q> component, Q quizQuestion) {
        if (component != null && component.getId() != null) {
            component.setQuestion(quizQuestion);
        }
    }
}
