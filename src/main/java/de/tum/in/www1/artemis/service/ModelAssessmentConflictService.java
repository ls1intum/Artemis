package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.*;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.*;
import de.tum.in.www1.artemis.repository.*;

@Service
public class ModelAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(ModelAssessmentConflictService.class);

    private ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    private ConflictingResultService conflictingResultService;

    private ConflictingResultRepository conflictingResultRepository;

    public ModelAssessmentConflictService(ModelAssessmentConflictRepository modelAssessmentConflictRepository, ConflictingResultService conflictingResultService,
            ConflictingResultRepository conflictingResultRepository) {
        this.modelAssessmentConflictRepository = modelAssessmentConflictRepository;
        this.conflictingResultService = conflictingResultService;
        this.conflictingResultRepository = conflictingResultRepository;
    }

    public List<ModelAssessmentConflict> createConflicts(Map<String, List<Feedback>> elementConflictingFeedbackMapping, Result causingResult) {
        List<ModelAssessmentConflict> conflicts = new ArrayList<>(elementConflictingFeedbackMapping.size());
        elementConflictingFeedbackMapping.forEach((elementID, feedbacksInConflict) -> {
            ModelAssessmentConflict conflict = createConflict(elementID, causingResult, feedbacksInConflict);
            conflicts.add(conflict);
        });
        return conflicts;
    }

    public ModelAssessmentConflict createConflict(String causingModelElementId, Result causingResult, List<Feedback> feedbacksInConflict) {
        ModelAssessmentConflict conflict = new ModelAssessmentConflict();
        Set<ConflictingResult> resultsInConflict = new HashSet<>();
        feedbacksInConflict.forEach(feedback -> {
            ConflictingResult conflictingResult = conflictingResultService.createConflictingResult(conflict, feedback);
            resultsInConflict.add(conflictingResult);
        });
        ConflictingResult causingConflictingResult = conflictingResultService.createConflictingResult(conflict, causingModelElementId, causingResult);
        conflict.setCausingConflictingResult(causingConflictingResult);
        conflict.setResultsInConflict(resultsInConflict);
        conflict.setCreationDate(ZonedDateTime.now());
        conflict.setState(EscalationState.UNHANDLED);
        return conflict;
    }

    public List<ModelAssessmentConflict> getConflictsForResult(Result result) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        return existingConflicts;
    }

    public List<ModelAssessmentConflict> getUnresolvedConflictsForResult(Result result) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        return existingConflicts.stream().filter(modelAssessmentConflict -> !modelAssessmentConflict.isResolved()).collect(Collectors.toList());
    }

    public void addMissingConflicts(Result causingResult, List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        newConflictingFeedbacks.keySet().forEach(modelElementId -> {
            Optional<ModelAssessmentConflict> foundExistingConflict = existingConflicts.stream()
                    .filter(existingConflict -> existingConflict.getCausingConflictingResult().getModelElementId().equals(modelElementId)).findFirst();
            if (!foundExistingConflict.isPresent()) {
                ModelAssessmentConflict newConflict = createConflict(modelElementId, causingResult, newConflictingFeedbacks.get(modelElementId));
                existingConflicts.add(newConflict);
            }
        });
    }

    public void updateExistingConflicts(List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        existingConflicts.forEach(conflict -> {
            List<Feedback> newFeedbacks = newConflictingFeedbacks.get(conflict.getCausingConflictingResult().getModelElementId());
            if (newFeedbacks != null) {
                Set<ConflictingResult> updatedResultsInConflict = conflictingResultService.updateExistingConflictingResults(conflict, conflict.getResultsInConflict(),
                        newFeedbacks);
                conflict.setResultsInConflict(updatedResultsInConflict);
            }
            else {
                resolveConflict(conflict);
            }
        });
    }

    public void saveConflicts(List<ModelAssessmentConflict> conflicts) {
        modelAssessmentConflictRepository.saveAll(conflicts);
    }

    public void resolveConflict(ModelAssessmentConflict conflict) {
        switch (conflict.getState()) {
        case UNHANDLED:
            conflict.setState(EscalationState.RESOLVED_BY_CAUSER);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            conflict.setState(EscalationState.RESOLVED_BY_OTHER_TUTORS);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        case ESCALATED_TO_INSTRUCTOR:
            conflict.setState(EscalationState.RESOLVED_BY_INSTRUCTOR);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        default:
            log.error("Tried to resolve already resolved conflict {}", conflict);
            break;
        }
    }

}
