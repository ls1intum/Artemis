package de.tum.in.www1.artemis.service;

import java.util.*;

import org.slf4j.*;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.*;
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

    public ConflictingResult createConflictingResult(ModelAssessmentConflict conflict, String modelElementID, Result result) {
        ConflictingResult conflictingResult = new ConflictingResult();
        conflictingResult.setModelElementId(modelElementID);
        conflictingResult.setResult(result);
        conflictingResult.setConflict(conflict);
        return conflictingResult;
    }

    public void updateExistingConflictingResults(ModelAssessmentConflict conflict, Set<ConflictingResult> existingConflictingResults, List<Feedback> newFeedbacks) {
        existingConflictingResults.clear();
        newFeedbacks.forEach(feedback -> {
            Optional<ConflictingResult> existingConflictingResult = existingConflictingResults.stream()
                    .filter(conflictingResult -> conflictingResult.getResult().getId().equals(feedback.getResult().getId())).findFirst();
            if (existingConflictingResult.isPresent()) {
                existingConflictingResults.add(existingConflictingResult.get());
            }
            else {
                existingConflictingResults.add(createConflictingResult(conflict, feedback));
            }
        });
    }
}
