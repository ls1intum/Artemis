package de.tum.in.www1.artemis.domain.quiz;

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
                    quizQuestion.getQuizQuestionStatistic().setQuizQuestion(quizQuestion);
                }
                // do the same for answerOptions (if quizQuestion is multiple choice)
                if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                    MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic) mcQuestion.getQuizQuestionStatistic();
                    // reconnect answerCounters
                    for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
                        if (answerCounter.getId() != null) {
                            answerCounter.setMultipleChoiceQuestionStatistic(mcStatistic);
                        }
                    }
                    // reconnect answerOptions
                    for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                        if (answerOption.getId() != null) {
                            answerOption.setQuestion(mcQuestion);
                        }
                    }
                }
                if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                    DragAndDropQuestionStatistic dragAndDropStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
                    // reconnect dropLocations
                    for (DropLocation dropLocation : dragAndDropQuestion.getDropLocations()) {
                        if (dropLocation.getId() != null) {
                            dropLocation.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dragItems
                    for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                        if (dragItem.getId() != null) {
                            dragItem.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dropLocationCounters
                    for (DropLocationCounter dropLocationCounter : dragAndDropStatistic.getDropLocationCounters()) {
                        if (dropLocationCounter.getId() != null) {
                            dropLocationCounter.setDragAndDropQuestionStatistic(dragAndDropStatistic);
                            dropLocationCounter.getDropLocation().setQuestion(dragAndDropQuestion);
                        }
                    }
                }
                if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                    ShortAnswerQuestionStatistic shortAnswerStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
                    // reconnect spots
                    for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
                        if (spot.getId() != null) {
                            spot.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect solutions
                    for (ShortAnswerSolution solution : shortAnswerQuestion.getSolutions()) {
                        if (solution.getId() != null) {
                            solution.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect spotCounters
                    for (ShortAnswerSpotCounter shortAnswerSpotCounter : shortAnswerStatistic.getShortAnswerSpotCounters()) {
                        if (shortAnswerSpotCounter.getId() != null) {
                            shortAnswerSpotCounter.setShortAnswerQuestionStatistic(shortAnswerStatistic);
                            shortAnswerSpotCounter.getSpot().setQuestion(shortAnswerQuestion);
                        }
                    }
                }
            }
        }
    }
}
