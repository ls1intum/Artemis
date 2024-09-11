package de.tum.cit.aet.artemis.service.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizConfiguration;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionComponent;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatisticComponent;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.quiz.repository.ShortAnswerMappingRepository;

@Profile(PROFILE_CORE)
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
            if (quizQuestion.getQuizQuestionStatistic() == null) {
                quizQuestion.initializeStatistic();
            }

            if (quizQuestion instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                fixReferenceMultipleChoice(multipleChoiceQuestion);
            }
            else if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                fixReferenceDragAndDrop(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                fixReferenceShortAnswer(shortAnswerQuestion);
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

    /**
     * Fix references of Multiple Choice Question before saving to database
     *
     * @param multipleChoiceQuestion the MultipleChoiceQuestion which references are to be fixed
     */
    private void fixReferenceMultipleChoice(MultipleChoiceQuestion multipleChoiceQuestion) {
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = (MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic();
        fixComponentReference(multipleChoiceQuestion, multipleChoiceQuestion.getAnswerOptions(), answerOption -> {
            multipleChoiceQuestionStatistic.addAnswerOption(answerOption);
            return null;
        });
        removeCounters(multipleChoiceQuestion.getAnswerOptions(), multipleChoiceQuestionStatistic.getAnswerCounters());
    }

    /**
     * Fix references of Drag and Drop Question before saving to database
     *
     * @param dragAndDropQuestion the DragAndDropQuestion which references are to be fixed
     */
    private void fixReferenceDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
        fixComponentReference(dragAndDropQuestion, dragAndDropQuestion.getDropLocations(), dropLocation -> {
            dragAndDropQuestionStatistic.addDropLocation(dropLocation);
            return null;
        });
        removeCounters(dragAndDropQuestion.getDropLocations(), dragAndDropQuestionStatistic.getDropLocationCounters());
        saveCorrectMappingsInIndicesDragAndDrop(dragAndDropQuestion);
    }

    /**
     * Fix references of Short Answer Question before saving to database
     *
     * @param shortAnswerQuestion the ShortAnswerQuestion which references are to be fixed
     */
    private void fixReferenceShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        ShortAnswerQuestionStatistic shortAnswerQuestionStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
        fixComponentReference(shortAnswerQuestion, shortAnswerQuestion.getSpots(), shortAnswerSpot -> {
            shortAnswerQuestionStatistic.addSpot(shortAnswerSpot);
            return null;
        });
        removeCounters(shortAnswerQuestion.getSpots(), shortAnswerQuestionStatistic.getShortAnswerSpotCounters());
        saveCorrectMappingsInIndicesShortAnswer(shortAnswerQuestion);
    }

    /**
     * Fix reference of the given components which belong to the given quizQuestion and apply the callback for each component.
     *
     * @param quizQuestion the QuizQuestion of which the given components belong to
     * @param components   the QuizQuestionComponent of which the references are to be fixed
     * @param callback     the Function that is applied for each given component
     */
    private <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> void fixComponentReference(Q quizQuestion, Collection<C> components, Function<C, Void> callback) {
        for (C component : components) {
            component.setQuestion(quizQuestion);
            callback.apply(component);
        }
    }

    /**
     * Remove statisticComponents that are not associated with any of the given components.
     *
     * @param components          the Collection of QuizQuestionComponent to be checked
     * @param statisticComponents the Collection of QuizQuestionStatisticComponent to be removed
     */
    private <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion, SC extends QuizQuestionStatisticComponent<S, C, Q>, S extends QuizQuestionStatistic> void removeCounters(
            Collection<C> components, Collection<SC> statisticComponents) {
        Set<SC> toDelete = new HashSet<>();
        for (SC statisticComponent : statisticComponents) {
            if (statisticComponent.getId() != null) {
                if (!(components.contains(statisticComponent.getQuizQuestionComponent()))) {
                    statisticComponent.setQuizQuestionComponent(null);
                    toDelete.add(statisticComponent);
                }
            }
        }
        statisticComponents.removeAll(toDelete);
    }

    /**
     * remove dragItem and dropLocation from correct mappings and set dragItemIndex and dropLocationIndex instead
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndicesDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
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
    private <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> boolean findComponent(Collection<C> components, C componentToBeSearched, Function<C, Void> foundCallback) {
        for (C component : components) {
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
