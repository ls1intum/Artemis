package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.DragAndDropMappingRepository;
import de.tum.in.www1.artemis.repository.ShortAnswerMappingRepository;

@Service
public abstract class QuizService<T extends QuizConfiguration> {

    private final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final DragAndDropMappingRepository dragAndDropMappingRepository;

    private final ShortAnswerMappingRepository shortAnswerMappingRepository;

    protected abstract void preReferenceFix(T quizConfiguration);

    protected abstract void preSave(T quizConfiguration);

    protected abstract T saveAndFlush(QuizConfiguration quizConfiguration);

    protected abstract void preSaveReturn(T quizConfiguration);

    protected QuizService(DragAndDropMappingRepository dragAndDropMappingRepository, ShortAnswerMappingRepository shortAnswerMappingRepository) {
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
        this.shortAnswerMappingRepository = shortAnswerMappingRepository;
    }

    /**
     * Save configuration of a quiz
     *
     * @param quizConfiguration the configuration of a quiz to be saved
     * @return QuizConfiguration the configuration of a quiz that has been saved
     */
    public T save(T quizConfiguration) {
        preReferenceFix(quizConfiguration);

        // fix references in all questions (step 1/2)
        for (var quizQuestion : quizConfiguration.getQuizQuestions()) {
            if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                var quizQuestionStatistic = (MultipleChoiceQuestionStatistic) mcQuestion.getQuizQuestionStatistic();
                if (quizQuestionStatistic == null) {
                    quizQuestionStatistic = new MultipleChoiceQuestionStatistic();
                    mcQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
                    quizQuestionStatistic.setQuizQuestion(mcQuestion);
                }

                for (var answerOption : mcQuestion.getAnswerOptions()) {
                    quizQuestionStatistic.addAnswerOption(answerOption);
                }

                // if an answerOption was removed then remove the associated AnswerCounters implicitly
                Set<AnswerCounter> answerCounterToDelete = new HashSet<>();
                for (AnswerCounter answerCounter : quizQuestionStatistic.getAnswerCounters()) {
                    if (answerCounter.getId() != null) {
                        if (!(mcQuestion.getAnswerOptions().contains(answerCounter.getAnswer()))) {
                            answerCounter.setAnswer(null);
                            answerCounterToDelete.add(answerCounter);
                        }
                    }
                }
                quizQuestionStatistic.getAnswerCounters().removeAll(answerCounterToDelete);
            }
            else if (quizQuestion instanceof DragAndDropQuestion dndQuestion) {
                var quizQuestionStatistic = (DragAndDropQuestionStatistic) dndQuestion.getQuizQuestionStatistic();
                if (quizQuestionStatistic == null) {
                    quizQuestionStatistic = new DragAndDropQuestionStatistic();
                    dndQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
                    quizQuestionStatistic.setQuizQuestion(dndQuestion);
                }

                for (var dropLocation : dndQuestion.getDropLocations()) {
                    quizQuestionStatistic.addDropLocation(dropLocation);
                }

                // if a dropLocation was removed then remove the associated AnswerCounters implicitly
                Set<DropLocationCounter> dropLocationCounterToDelete = new HashSet<>();
                for (DropLocationCounter dropLocationCounter : quizQuestionStatistic.getDropLocationCounters()) {
                    if (dropLocationCounter.getId() != null) {
                        if (!(dndQuestion.getDropLocations().contains(dropLocationCounter.getDropLocation()))) {
                            dropLocationCounter.setDropLocation(null);
                            dropLocationCounterToDelete.add(dropLocationCounter);
                        }
                    }
                }
                quizQuestionStatistic.getDropLocationCounters().removeAll(dropLocationCounterToDelete);

                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndices(dndQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion saQuestion) {
                var quizQuestionStatistic = (ShortAnswerQuestionStatistic) saQuestion.getQuizQuestionStatistic();
                if (quizQuestionStatistic == null) {
                    quizQuestionStatistic = new ShortAnswerQuestionStatistic();
                    saQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
                    quizQuestionStatistic.setQuizQuestion(quizQuestion);
                }

                for (var spot : saQuestion.getSpots()) {
                    spot.setQuestion(saQuestion);
                    quizQuestionStatistic.addSpot(spot);
                }

                // if a spot was removed then remove the associated spotCounters implicitly
                Set<ShortAnswerSpotCounter> spotCounterToDelete = new HashSet<>();
                for (ShortAnswerSpotCounter spotCounter : quizQuestionStatistic.getShortAnswerSpotCounters()) {
                    if (spotCounter.getId() != null) {
                        if (!(saQuestion.getSpots().contains(spotCounter.getSpot()))) {
                            spotCounter.setSpot(null);
                            spotCounterToDelete.add(spotCounter);
                        }
                    }
                }
                quizQuestionStatistic.getShortAnswerSpotCounters().removeAll(spotCounterToDelete);

                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndicesShortAnswer(saQuestion);
            }
        }

        preSave(quizConfiguration);

        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        // and delete the now orphaned entries from the database
        log.debug("Save quiz to database: {}", quizConfiguration);
        T savedQuizConfiguration = saveAndFlush(quizConfiguration);

        // fix references in all drag and drop questions and short answer questions (step 2/2)
        for (QuizQuestion quizQuestion : savedQuizConfiguration.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                // restore references from index after save
                restoreCorrectMappingsFromIndices(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                // restore references from index after save
                restoreCorrectMappingsFromIndicesShortAnswer(shortAnswerQuestion);
            }
        }

        preSaveReturn(savedQuizConfiguration);

        return savedQuizConfiguration;
    }

    /**
     * remove dragItem and dropLocation from correct mappings and set dragItemIndex and dropLocationIndex instead
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndices(DragAndDropQuestion dragAndDropQuestion) {
        List<DragAndDropMapping> mappingsToBeRemoved = new ArrayList<>();
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getDragItem() == null || mapping.getDropLocation() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // drag item index
            DragItem dragItem = mapping.getDragItem();
            boolean dragItemFound = false;
            for (DragItem questionDragItem : dragAndDropQuestion.getDragItems()) {
                if (dragItem.equals(questionDragItem)) {
                    dragItemFound = true;
                    mapping.setDragItemIndex(dragAndDropQuestion.getDragItems().indexOf(questionDragItem));
                    mapping.setDragItem(null);
                    break;
                }
            }

            // drop location index
            DropLocation dropLocation = mapping.getDropLocation();
            boolean dropLocationFound = false;
            for (DropLocation questionDropLocation : dragAndDropQuestion.getDropLocations()) {
                if (dropLocation.equals(questionDropLocation)) {
                    dropLocationFound = true;
                    mapping.setDropLocationIndex(dragAndDropQuestion.getDropLocations().indexOf(questionDropLocation));
                    mapping.setDropLocation(null);
                    break;
                }
            }

            // if one of them couldn't be found, remove the mapping entirely
            if (!dragItemFound || !dropLocationFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (DragAndDropMapping mapping : mappingsToBeRemoved) {
            dragAndDropQuestion.removeCorrectMapping(mapping);
        }
    }

    /**
     * remove solutions and spots from correct mappings and set solutionIndex and spotIndex instead
     *
     * @param shortAnswerQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndicesShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        List<ShortAnswerMapping> mappingsToBeRemoved = new ArrayList<>();
        for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getSolution() == null || mapping.getSpot() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // solution index
            ShortAnswerSolution solution = mapping.getSolution();
            boolean solutionFound = false;
            for (ShortAnswerSolution questionSolution : shortAnswerQuestion.getSolutions()) {
                if (solution.equals(questionSolution)) {
                    solutionFound = true;
                    mapping.setShortAnswerSolutionIndex(shortAnswerQuestion.getSolutions().indexOf(questionSolution));
                    mapping.setSolution(null);
                    break;
                }
            }

            // replace spot
            ShortAnswerSpot spot = mapping.getSpot();
            boolean spotFound = false;
            for (ShortAnswerSpot questionSpot : shortAnswerQuestion.getSpots()) {
                if (spot.equals(questionSpot)) {
                    spotFound = true;
                    mapping.setShortAnswerSpotIndex(shortAnswerQuestion.getSpots().indexOf(questionSpot));
                    mapping.setSpot(null);
                    break;
                }
            }

            // if one of them couldn't be found, remove the mapping entirely
            if (!solutionFound || !spotFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (ShortAnswerMapping mapping : mappingsToBeRemoved) {
            shortAnswerQuestion.removeCorrectMapping(mapping);
        }
    }

    /**
     * restore dragItem and dropLocation for correct mappings using dragItemIndex and dropLocationIndex
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndices(DragAndDropQuestion dragAndDropQuestion) {
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // drag item
            mapping.setDragItem(dragAndDropQuestion.getDragItems().get(mapping.getDragItemIndex()));
            // drop location
            mapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(mapping.getDropLocationIndex()));
            // set question
            mapping.setQuestion(dragAndDropQuestion);
            // save mapping
            dragAndDropMappingRepository.save(mapping);
        }
    }

    /**
     * restore solution and spots for correct mappings using solutionIndex and spotIndex
     *
     * @param shortAnswerQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndicesShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
            // solution
            mapping.setSolution(shortAnswerQuestion.getSolutions().get(mapping.getShortAnswerSolutionIndex()));
            // spot
            mapping.setSpot(shortAnswerQuestion.getSpots().get(mapping.getShortAnswerSpotIndex()));
            // set question
            mapping.setQuestion(shortAnswerQuestion);
            // save mapping
            shortAnswerMappingRepository.save(mapping);
        }
    }
}
