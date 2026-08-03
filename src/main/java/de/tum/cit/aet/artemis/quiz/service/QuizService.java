package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizConfiguration;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;

@Profile(PROFILE_CORE)
@Lazy
@Service
public abstract class QuizService<T extends QuizConfiguration> {

    /**
     * Save the given QuizConfiguration to the database according to the implementor.
     *
     * @param quizConfiguration the QuizConfiguration to be saved.
     * @return the saved QuizConfiguration
     */
    protected abstract T saveAndFlush(T quizConfiguration);

    protected QuizService() {
    }

    /**
     * Save the given QuizConfiguration
     *
     * @param quizConfiguration the QuizConfiguration to be saved
     * @return saved QuizConfiguration
     */
    public T save(T quizConfiguration) {
        // fix references in all questions: make sure there is exactly one statistics counter per (still existing) component. The components themselves (answer options / drop
        // locations / drag items / correct mappings, resp. spots / solutions / correct mappings) are stored id-based in the question's JSON content, so no back-reference or index
        // fixup is needed anymore.
        for (var quizQuestion : quizConfiguration.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() == null) {
                quizQuestion.initializeStatistic();
            }

            switch (quizQuestion) {
                case MultipleChoiceQuestion multipleChoiceQuestion -> fixReferenceMultipleChoice(multipleChoiceQuestion);
                case DragAndDropQuestion dragAndDropQuestion -> fixReferenceDragAndDrop(dragAndDropQuestion);
                case ShortAnswerQuestion shortAnswerQuestion -> fixReferenceShortAnswer(shortAnswerQuestion);
                default -> {
                }
            }
        }

        return saveAndFlush(quizConfiguration);
    }

    /**
     * Fix references of Multiple Choice Question before saving to database: make sure there is exactly one answer counter per (still existing) answer option.
     *
     * @param multipleChoiceQuestion the MultipleChoiceQuestion which references are to be fixed
     */
    private void fixReferenceMultipleChoice(MultipleChoiceQuestion multipleChoiceQuestion) {
        // mint ids for any answer option added without one (e.g. via getAnswerOptions().add(...)) so the counters below are keyed by a stable id
        multipleChoiceQuestion.assignMissingComponentIds();
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = (MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic();
        // ensure a counter exists for every answer option
        for (AnswerOption answerOption : multipleChoiceQuestion.getAnswerOptions()) {
            multipleChoiceQuestionStatistic.addAnswerOption(answerOption);
        }
        // remove counters whose answer option no longer exists
        Set<Long> answerOptionIds = multipleChoiceQuestion.getAnswerOptions().stream().map(AnswerOption::getId).collect(Collectors.toSet());
        Set<AnswerCounter> countersToRemove = new HashSet<>();
        for (AnswerCounter counter : multipleChoiceQuestionStatistic.getAnswerCounters()) {
            if (counter.getAnswerId() == null || !answerOptionIds.contains(counter.getAnswerId())) {
                countersToRemove.add(counter);
            }
        }
        multipleChoiceQuestionStatistic.getAnswerCounters().removeAll(countersToRemove);
    }

    /**
     * Fix references of Drag and Drop Question before saving to database: make sure there is exactly one drop-location counter per (still existing) drop location.
     *
     * @param dragAndDropQuestion the DragAndDropQuestion which references are to be fixed
     */
    private void fixReferenceDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
        // mint ids for any drop location / drag item added without one (e.g. via getDropLocations().add(...)) so the counters below are keyed by a stable id
        dragAndDropQuestion.assignMissingComponentIds();
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
        // ensure a counter exists for every drop location
        for (DropLocation dropLocation : dragAndDropQuestion.getDropLocations()) {
            dragAndDropQuestionStatistic.addDropLocation(dropLocation);
        }
        // remove counters whose drop location no longer exists
        Set<Long> dropLocationIds = dragAndDropQuestion.getDropLocations().stream().map(DropLocation::getId).collect(Collectors.toSet());
        Set<DropLocationCounter> countersToRemove = new HashSet<>();
        for (DropLocationCounter counter : dragAndDropQuestionStatistic.getDropLocationCounters()) {
            if (counter.getDropLocationId() == null || !dropLocationIds.contains(counter.getDropLocationId())) {
                countersToRemove.add(counter);
            }
        }
        dragAndDropQuestionStatistic.getDropLocationCounters().removeAll(countersToRemove);
    }

    /**
     * Fix references of Short Answer Question before saving to database: make sure there is exactly one spot counter per (still existing) spot.
     *
     * @param shortAnswerQuestion the ShortAnswerQuestion which references are to be fixed
     */
    private void fixReferenceShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        // mint ids for any spot / solution added without one (e.g. via getSpots().add(...)) so the counters below are keyed by a stable id
        shortAnswerQuestion.assignMissingComponentIds();
        ShortAnswerQuestionStatistic shortAnswerQuestionStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
        // ensure a counter exists for every spot
        for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
            shortAnswerQuestionStatistic.addSpot(spot);
        }
        // remove counters whose spot no longer exists
        Set<Long> spotIds = shortAnswerQuestion.getSpots().stream().map(ShortAnswerSpot::getId).collect(Collectors.toSet());
        Set<ShortAnswerSpotCounter> countersToRemove = new HashSet<>();
        for (ShortAnswerSpotCounter counter : shortAnswerQuestionStatistic.getShortAnswerSpotCounters()) {
            if (counter.getSpotId() == null || !spotIds.contains(counter.getSpotId())) {
                countersToRemove.add(counter);
            }
        }
        shortAnswerQuestionStatistic.getShortAnswerSpotCounters().removeAll(countersToRemove);
    }
}
