package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.*;
import de.tum.in.www1.artemis.repository.*;

@Service
public class ModelAssessmentConflictService {

    private ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    private ConflictingResultRepository conflictingResultRepository;

    public ModelAssessmentConflictService(ModelAssessmentConflictRepository modelAssessmentConflictRepository, ConflictingResultRepository conflictingResultRepository) {
        this.modelAssessmentConflictRepository = modelAssessmentConflictRepository;
        this.conflictingResultRepository = conflictingResultRepository;
    }

    public List<ModelAssessmentConflict> createConflicts(Map<String, List<Feedback>> elementConflictingFeedbackMapping, Result causingResult) {
        List<ModelAssessmentConflict> conflicts = new ArrayList<>(elementConflictingFeedbackMapping.size());
        elementConflictingFeedbackMapping.forEach((elementID, feedbacks) -> {
            Set<ConflictingResult> resultsInConflict = new HashSet<>();
            feedbacks.forEach(feedback -> {
                ConflictingResult conflictingResult = new ConflictingResult();
                conflictingResult.setModelElementId(feedback.getReferenceElementId());
                conflictingResult.setResult(feedback.getResult());
                resultsInConflict.add(conflictingResult);
            });
            ConflictingResult causingConflictingResult = new ConflictingResult();
            causingConflictingResult.setModelElementId(elementID);
            causingConflictingResult.setResult(causingResult);
            ModelAssessmentConflict conflict = new ModelAssessmentConflict();
            conflict.setCausingConflictingResult(causingConflictingResult);
            conflict.setResultsInConflict(resultsInConflict);
            conflict.setCreationDate(ZonedDateTime.now());
            conflict.setState(EscalationState.UNHANDLED);
            conflicts.add(conflict);
        });
        return conflicts;
    }

    public void saveConflicts(List<ModelAssessmentConflict> conflicts) {
        conflicts.forEach(modelAssessmentConflict -> {
            conflictingResultRepository.save(modelAssessmentConflict.getCausingResult());
            conflictingResultRepository.saveAll(modelAssessmentConflict.getResultsInConflict());
            modelAssessmentConflictRepository.save(modelAssessmentConflict);
        });
        // conflicts.forEach(modelAssessmentConflictRepository::save);
        // modelAssessmentConflictRepository.saveAll(conflicts);
    }

}
