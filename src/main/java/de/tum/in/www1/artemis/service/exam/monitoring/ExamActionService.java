package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.Collection;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.*;
import de.tum.in.www1.artemis.repository.ExamActionRepository;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.ConnectionUpdatedActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.SavedExerciseActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.StartedExamActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.SwitchedExerciseActionDTO;
import de.tum.in.www1.artemis.service.exam.ExamSessionService;

@Service
public class ExamActionService {

    private final ExamActionRepository examActionRepository;

    private final ExamSessionService examSessionService;

    private final ExerciseService exerciseService;

    private final SubmissionService submissionService;

    public ExamActionService(ExamActionRepository examActionRepository, ExamSessionService examSessionService, ExerciseService exerciseService,
            SubmissionService submissionService) {
        this.examActionRepository = examActionRepository;
        this.examSessionService = examSessionService;
        this.exerciseService = exerciseService;
        this.submissionService = submissionService;
    }

    /**
     * To avoid direct access to the {@link ExamActionRepository}, we use delegation save the {@link ExamAction}.
     * @param examAction {@link ExamAction} to save
     * @return saved {@link ExamAction}
     */
    public ExamAction save(ExamAction examAction) {
        return this.examActionRepository.save(examAction);
    }

    /**
     * To avoid direct access to the {@link ExamActionRepository}, we use delegation save the {@link ExamAction}s.
     * @param examActions {@link ExamAction}s to save
     * @return saved {@link ExamAction}s
     */
    public Collection<ExamAction> saveAll(Collection<ExamAction> examActions) {
        return this.examActionRepository.saveAll(examActions);
    }

    /**
     * This method maps the {@link ExamActionDTO} to the associated {@link ExamAction}.
     * @param examActionDTO received action
     * @return mapped and transformed action
     *
     */
    public ExamAction mapExamAction(ExamActionDTO examActionDTO) {
        ExamAction action = null;
        switch (examActionDTO.getType()) {
            case STARTED_EXAM -> {
                action = new StartedExamAction(examSessionService.findById(((StartedExamActionDTO) examActionDTO).getExamSessionId()).orElse(null));
            }
            case ENDED_EXAM -> {
                action = new EndedExamAction();
            }
            case HANDED_IN_EARLY -> {
                action = new HandedInEarlyAction();
            }
            case CONTINUED_AFTER_HAND_IN_EARLY -> {
                action = new ContinuedAfterHandedInEarlyAction();
            }
            case SWITCHED_EXERCISE -> {
                action = new SwitchedExerciseAction(exerciseService.findById(((SwitchedExerciseActionDTO) examActionDTO).getExerciseId()).orElse(null));
            }
            case SAVED_EXERCISE -> {
                SavedExerciseActionDTO savedExerciseActionDTO = (SavedExerciseActionDTO) examActionDTO;
                action = new SavedExerciseAction(savedExerciseActionDTO.isForced(), savedExerciseActionDTO.isFailed(), savedExerciseActionDTO.isAutomatically(),
                        submissionService.findById(savedExerciseActionDTO.getSubmissionId()).orElse(null));
            }
            case CONNECTION_UPDATED -> {
                action = new ConnectionUpdatedAction(((ConnectionUpdatedActionDTO) examActionDTO).isConnected());
            }
        }
        if (action != null) {
            action.setTimestamp(examActionDTO.getTimestamp());
            action.setType(examActionDTO.getType());
        }
        return action;
    }
}
