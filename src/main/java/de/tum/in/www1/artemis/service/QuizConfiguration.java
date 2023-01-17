package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.*;

public interface QuizConfiguration {

    Long getId();

    void setMaxPoints(Double maxPoints);

    Double getOverallQuizPoints();

    QuizPointStatistic getQuizPointStatistic();

    void setQuizPointStatistic(QuizPointStatistic quizPointStatistic);

    void recalculatePointCounters();

    List<QuizQuestion> getQuizQuestions();

    Set<QuizBatch> getQuizBatches();

    QuizMode getQuizMode();

    void setDueDate(ZonedDateTime dueDate);

    Integer getDuration();

    boolean isCourseExercise();

    void setQuestionParent(QuizQuestion quizQuestion);

    void setQuizBatchParent(QuizBatch quizBatch);

    /**
     * Recreate missing pointers from children to parents that were removed by @JSONIgnore
     */
    default void reconnectJSONIgnoreAttributes() {
        // iterate through quizQuestions to add missing pointer back to quizExercise
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

        // reconnect pointCounters
        for (PointCounter pointCounter : getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getId() != null) {
                pointCounter.setQuizPointStatistic(getQuizPointStatistic());
            }
        }

        if (getQuizBatches() != null) {
            for (QuizBatch quizBatch : getQuizBatches()) {
                setQuizBatchParent(quizBatch);
            }
        }
    }
}
