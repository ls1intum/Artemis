package de.tum.in.www1.artemis.domain.exam.monitoring.factory;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActionFactory;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.SavedExerciseAction;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.SavedExerciseActionDTO;

@Service
public class SavedExerciseActionFactory implements ExamActionFactory {

    private final SubmissionService submissionService;

    public SavedExerciseActionFactory(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Override
    public ExamAction create(ExamActionDTO examActionDTO) {
        SavedExerciseActionDTO savedExerciseActionDTO = (SavedExerciseActionDTO) examActionDTO;
        Submission submission = submissionService.findById(savedExerciseActionDTO.getSubmissionId()).orElse(null);
        return new SavedExerciseAction(savedExerciseActionDTO.isForced(), savedExerciseActionDTO.isFailed(), savedExerciseActionDTO.isAutomatically(), submission);
    }

    @Override
    public boolean match(ExamActionType examActionType) {
        return examActionType == ExamActionType.SAVED_EXERCISE;
    }
}
