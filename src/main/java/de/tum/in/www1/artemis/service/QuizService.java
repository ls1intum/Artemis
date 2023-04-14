package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.DragAndDropMappingRepository;
import de.tum.in.www1.artemis.repository.ShortAnswerMappingRepository;

@Service
public abstract class QuizService<T extends QuizConfiguration> {

    private final DragAndDropMappingRepository dragAndDropMappingRepository;

    private final ShortAnswerMappingRepository shortAnswerMappingRepository;

    /**
     * Save the given QuizConfiguration to the database according to the implementor.
     *
     * @param quizConfiguration the QuizConfiguration to be saved.
     * @return the saved QuizConfiguration
     */
    protected abstract T saveAndFlush(T quizConfiguration);

    protected QuizService(DragAndDropMappingRepository dragAndDropMappingRepository, ShortAnswerMappingRepository shortAnswerMappingRepository) {
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
        this.shortAnswerMappingRepository = shortAnswerMappingRepository;
    }

    /**
     * Save the given QuizConfiguration
     *
     * @param quizConfiguration the QuizConfiguration to be saved
     * @return saved QuizConfiguration
     */
    public T save(T quizConfiguration) {
        // fix references in all questions (step 1/2)
        for (var quizQuestion : quizConfiguration.getQuizQuestions()) {
            if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                fixReferenceMultipleChoice(mcQuestion);
            }
            else if (quizQuestion instanceof DragAndDropQuestion dndQuestion) {
                fixReferenceDragAndDrop(dndQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion saQuestion) {
                fixReferenceShortAnswer(saQuestion);
            }
        }

        T savedQuizConfiguration = saveAndFlush(quizConfiguration);

        // fix references in all drag and drop questions and short answer questions (step 2/2)
        for (QuizQuestion quizQuestion : savedQuizConfiguration.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                // restore references from index after save
                restoreCorrectMappingsFromIndicesDragAndDrop(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                // restore references from index after save
                restoreCorrectMappingsFromIndicesShortAnswer(shortAnswerQuestion);
            }
        }

        return savedQuizConfiguration;
    }

    private void fixReferenceMultipleChoice(MultipleChoiceQuestion mcQuestion) {
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

    private void fixReferenceDragAndDrop(DragAndDropQuestion dndQuestion) {
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

    private void fixReferenceShortAnswer(ShortAnswerQuestion saQuestion) {
        var quizQuestionStatistic = (ShortAnswerQuestionStatistic) saQuestion.getQuizQuestionStatistic();
        if (quizQuestionStatistic == null) {
            quizQuestionStatistic = new ShortAnswerQuestionStatistic();
            saQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
            quizQuestionStatistic.setQuizQuestion(saQuestion);
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
            boolean dragItemFound = findComponent(dragAndDropQuestion.getDragItems(), mapping.getDragItem(), questionDragItem -> {
                mapping.setDragItemIndex(dragAndDropQuestion.getDragItems().indexOf(questionDragItem));
                mapping.setDragItem(null);
                return null;
            });

            // drop location index
            boolean dropLocationFound = findComponent(dragAndDropQuestion.getDropLocations(), mapping.getDropLocation(), questionDropLocation -> {
                mapping.setDropLocationIndex(dragAndDropQuestion.getDropLocations().indexOf(questionDropLocation));
                mapping.setDropLocation(null);
                return null;
            });

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
            boolean solutionFound = findComponent(shortAnswerQuestion.getSolutions(), mapping.getSolution(), questionSolution -> {
                mapping.setShortAnswerSolutionIndex(shortAnswerQuestion.getSolutions().indexOf(questionSolution));
                mapping.setSolution(null);
                return null;
            });

            // replace spot
            boolean spotFound = findComponent(shortAnswerQuestion.getSpots(), mapping.getSpot(), questionSpot -> {
                mapping.setShortAnswerSpotIndex(shortAnswerQuestion.getSpots().indexOf(questionSpot));
                mapping.setSpot(null);
                return null;
            });

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
     * Find the given componentToBeSearched in components. If found, apply the given foundCallback.
     *
     * @param components            the collection of QuizQuestionComponent to be searched from
     * @param componentToBeSearched the QuizQuestionComponent to be searched
     * @param foundCallback         the callback to be applied if the given componentToBeSearched is found
     * @return true if the given componentToBeSearched is found or false otherwise
     */
    private <T1 extends QuizQuestionComponent<T2>, T2 extends QuizQuestion> boolean findComponent(Collection<T1> components, T1 componentToBeSearched,
            Function<T1, Void> foundCallback) {
        for (T1 component : components) {
            if (componentToBeSearched.equals(component)) {
                foundCallback.apply(component);
                return true;
            }
        }
        return false;
    }

    /**
     * restore dragItem and dropLocation for correct mappings using dragItemIndex and dropLocationIndex
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndicesDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
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
