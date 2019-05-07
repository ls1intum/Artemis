package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public ConflictingResult createConflictingResult(ModelAssessmentConflict conflict, Feedback feedback) {
        ConflictingResult conflictingResult = new ConflictingResult();
        conflictingResult.setModelElementId(feedback.getReferenceElementId());
        conflictingResult.setResult(feedback.getResult());
        conflictingResult.setConflict(conflict);
        return conflictingResult;
    }

    public ConflictingResult createConflictingResult(String modelElementID, Result result) {
        ConflictingResult conflictingResult = new ConflictingResult();
        conflictingResult.setModelElementId(modelElementID);
        conflictingResult.setResult(result);
        // conflictingResult.setConflict(conflict);
        return conflictingResult;
    }

    /**
     * Removes
     *
     * @param conflict     the conflict object to update
     * @param newFeedbacks represents the current feedbacks that the given conflict object is in conflict with
     */
    public void updateExistingConflictingResults(ModelAssessmentConflict conflict, List<Feedback> newFeedbacks) {
        removeRemovedConflictingResults(conflict, newFeedbacks);
        addMissingConflictingResults(conflict, newFeedbacks);
    }

    @Transactional
    void removeRemovedConflictingResults(ModelAssessmentConflict conflict, List<Feedback> newFeedbacks) {
        Set<ConflictingResult> existingConflictingResultsCopy = new HashSet<>(conflict.getResultsInConflict());
        conflict.getResultsInConflict().clear();
        Set<String> newFeedbacksElementIds = new HashSet<>();
        newFeedbacks.forEach(feedback -> newFeedbacksElementIds.add(feedback.getReferenceElementId()));
        existingConflictingResultsCopy.stream().filter(conflictingResult -> newFeedbacksElementIds.contains(conflictingResult.getModelElementId()))// TODO remove foreign key fix
                .forEach(conflictingResult -> conflict.getResultsInConflict().add(conflictingResult));
    }

    @Transactional
    void addMissingConflictingResults(ModelAssessmentConflict conflict, List<Feedback> newFeedbacks) {
        Set<String> existingConflictingResultsElementIds = new HashSet<>();
        conflict.getResultsInConflict().forEach(conflictingResult -> existingConflictingResultsElementIds.add(conflictingResult.getModelElementId()));
        newFeedbacks.forEach(feedback -> {
            if (!existingConflictingResultsElementIds.contains(feedback.getReferenceElementId())) {
                conflict.getResultsInConflict().add(createConflictingResult(conflict, feedback));
            }
        });
    }

    public ModelAssessmentConflict filterDoubleConflictingResults(ModelAssessmentConflict conflict) {
        Set<ConflictingResult> existingConflictingResultsCopy = new HashSet<>(conflict.getResultsInConflict());
        conflict.getResultsInConflict().clear();
        existingConflictingResultsCopy.stream().filter(conflictingResult -> !conflictingResult.getId().equals(conflict.getCausingConflictingResult().getId()))
                .forEach(conflictingResult -> conflict.getResultsInConflict().add(conflictingResult));
        return conflict;
    }
}
