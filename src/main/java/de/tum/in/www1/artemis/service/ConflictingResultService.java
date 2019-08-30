package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.repository.ConflictingResultRepository;

@Service
public class ConflictingResultService {

    private final Logger log = LoggerFactory.getLogger(ConflictingResultService.class);

    private ConflictingResultRepository conflictingResultRepository;

    public ConflictingResultService(ConflictingResultRepository conflictingResultRepository) {
        this.conflictingResultRepository = conflictingResultRepository;
    }

    /**
     * Helper method for creating a conflictingResult instance with the elementID and result of the given feedback as well as the given conflict. Used to create an member of the
     * resultsInConflict list of the ModelAssessmentConflict object.
     *
     * @param conflict between a newly assessed model element and already persisted assessed model elements
     * @param feedback in conflict
     * @return conflictingResult instance
     */
    public ConflictingResult createConflictingResult(ModelAssessmentConflict conflict, Feedback feedback) {
        ConflictingResult conflictingResult = new ConflictingResult();
        conflictingResult.setModelElementId(feedback.getReferenceElementId());
        conflictingResult.setResult(feedback.getResult());
        conflictingResult.setConflict(conflict);
        return conflictingResult;
    }

    /**
     * Helper method for creating a ConflictingResult instance with the given modelElementID and result. Used to create the causingConflictingResult instance of the
     * ModelAssessmentConflict object
     *
     * @param modelElementID of the element that caused the conflict
     * @param result that caused the conflict
     * @return conflictingResult instance
     */
    public ConflictingResult createConflictingResult(String modelElementID, Result result) {
        ConflictingResult conflictingResult = new ConflictingResult();
        conflictingResult.setModelElementId(modelElementID);
        conflictingResult.setResult(result);
        return conflictingResult;
    }

    /**
     * Updates the given conflict's resultsInConflict to match the new list of feedbacks the conflict is in conflict with
     *
     * @param conflict     the conflict object to update
     * @param newFeedbacks represents the current feedbacks that the given conflict object is in conflict with
     */
    public void updateExistingConflictingResults(ModelAssessmentConflict conflict, List<Feedback> newFeedbacks) {
        removeRemovedConflictingResults(conflict, newFeedbacks);
        addMissingConflictingResults(conflict, newFeedbacks);
    }

    /**
     * Removes ConflictingResults from the resultsInConflict list of the given conflkict that are no longer in conflict
     *
     * @param conflict     the conflict object to update
     * @param newFeedbacks represents the current feedbacks that the given conflict object is in conflict with
     */
    @Transactional
    void removeRemovedConflictingResults(ModelAssessmentConflict conflict, List<Feedback> newFeedbacks) {
        Set<String> newFeedbacksElementIds = newFeedbacks.stream().map(feedback -> feedback.getReferenceElementId()).collect(Collectors.toSet());
        Set<ConflictingResult> newResultsInConflictCopy = conflict.getResultsInConflict().stream()
                .filter(conflictingResult -> newFeedbacksElementIds.contains(conflictingResult.getModelElementId())).collect(Collectors.toSet());
        conflict.getResultsInConflict().clear();
        newResultsInConflictCopy.forEach(conflictingResult -> conflict.getResultsInConflict().add(conflictingResult));
    }

    /**
     * Adds ConflictingResults to the resultsInConflict list that are missing according to the newFeedbacks list
     *
     * @param conflict     the conflict object to update
     * @param newFeedbacks represents the current feedbacks that the given conflict object is in conflict with
     */
    @Transactional
    void addMissingConflictingResults(ModelAssessmentConflict conflict, List<Feedback> newFeedbacks) {
        Set<String> existingConflictingResultsElementIds = conflict.getResultsInConflict().stream().map(conflictingResult -> conflictingResult.getModelElementId())
                .collect(Collectors.toSet());
        newFeedbacks.stream().filter(feedback -> !existingConflictingResultsElementIds.contains(feedback.getReferenceElementId()))
                .forEach(feedback -> conflict.getResultsInConflict().add(createConflictingResult(conflict, feedback)));
    }
}
