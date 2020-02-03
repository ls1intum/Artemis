package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ModelAssessmentConflictRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(ModelAssessmentConflictService.class);

    private final ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    private final ConflictingResultService conflictingResultService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResultRepository resultRepository;

    public ModelAssessmentConflictService(ModelAssessmentConflictRepository modelAssessmentConflictRepository, ConflictingResultService conflictingResultService,
            UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository) {
        this.modelAssessmentConflictRepository = modelAssessmentConflictRepository;
        this.conflictingResultService = conflictingResultService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
    }

    public ModelAssessmentConflict findOne(Long conflictId) {
        return modelAssessmentConflictRepository.findById(conflictId).orElseThrow(() -> new EntityNotFoundException("Entity with id " + conflictId + "does not exist"));
    }

    public List<ModelAssessmentConflict> getConflictsForExercise(Long exerciseId) {
        return modelAssessmentConflictRepository.findAllConflictsOfExercise(exerciseId);
    }

    /**
     * Retrieves all model assessment conflicts for a given submission, for which the current user is responsible
     *
     * @param submissionId The id of the submission
     * @return A list of all assessment conflicts for the given submission id
     */
    @Transactional(readOnly = true)
    public List<ModelAssessmentConflict> getConflictsForCurrentUserForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> conflictsForSubmission = getConflictsForSubmission(submissionId);
        if (conflictsForSubmission.isEmpty()) {
            return new ArrayList<>();
        }
        else {
            StudentParticipation studentParticipation = (StudentParticipation) conflictsForSubmission.get(0).getCausingConflictingResult().getResult().getParticipation();
            Exercise exercise = studentParticipation.getExercise();
            return conflictsForSubmission.stream().filter(conflict -> currentUserIsResponsibleForHandling(conflict, exercise)).collect(Collectors.toList());
        }
    }

    private List<ModelAssessmentConflict> getConflictsForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingSubmission(submissionId);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(existingConflicts);
        return existingConflicts;
    }

    private List<ModelAssessmentConflict> getConflictsForResult(Result result) {
        return modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
    }

    public List<ModelAssessmentConflict> getUnresolvedConflictsForResult(Result result) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsForResult(result);
        return existingConflicts.stream().filter(conflict -> !conflict.isResolved()).collect(Collectors.toList());
    }

    public List<ModelAssessmentConflict> getConflictsForResultWithState(Result result, EscalationState state) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsForResult(result);
        return existingConflicts.stream().filter(conflict -> conflict.getState().equals(state)).collect(Collectors.toList());
    }

    /**
     * Loads properties of the given conflicts that are needed by the conflict resolution view of the client
     * 
     * @param conflicts The conflicts for which properties should be loaded
     */
    public void loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(List<ModelAssessmentConflict> conflicts) {
        conflicts.forEach(conflict -> {
            conflict.getCausingConflictingResult()
                    .setResult(resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(conflict.getCausingConflictingResult().getResult().getId()).get());
            conflict.getResultsInConflict().forEach(conflictingResult -> conflictingResult
                    .setResult(resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(conflictingResult.getResult().getId()).get()));
        });
    }

    @Transactional
    public Exercise getExerciseOfConflict(Long conflictId) {
        ModelAssessmentConflict conflict = findOne(conflictId);
        StudentParticipation studentParticipation = (StudentParticipation) conflict.getCausingConflictingResult().getResult().getParticipation();
        return studentParticipation.getExercise();
    }

    public void saveConflicts(List<ModelAssessmentConflict> conflicts) {
        modelAssessmentConflictRepository.saveAll(conflicts);
    }

    /**
     * Updates the state of the given conflict by escalating the conflict to the next authority. The assessors or instructors then responsible for handling the conflict are getting
     * notified.
     * 
     * @param conflictId id of the conflict to escalate
     * @return escalated conflict of the given conflictId
     */
    @Transactional
    public ModelAssessmentConflict escalateConflict(Long conflictId) {
        ModelAssessmentConflict storedConflict = findOne(conflictId);
        if (storedConflict.isResolved()) {
            log.error("Escalating resolved conflict {} is not possible.", conflictId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict with id" + conflictId + "has already been resolved");
        }
        switch (storedConflict.getState()) {
        case UNHANDLED:
            // TODO Notify tutors
            storedConflict.setState(EscalationState.ESCALATED_TO_TUTORS_IN_CONFLICT);
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            // TODO Notify instructors
            storedConflict.setState(EscalationState.ESCALATED_TO_INSTRUCTOR);
            break;
        default:
            log.error("Escalating conflict {} with state {} failed .", conflictId, storedConflict.getState());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict: " + conflictId + " can´t be escalated");
        }
        modelAssessmentConflictRepository.save(storedConflict);
        return storedConflict;
    }

    /**
     * Adds new conflicts to the provided existingConflicts that are currently not present in the existingConflicts list but contained in the newConflictingFeedbacks mapping
     *
     * @param causingResult           Result that caused the conflicts in newConflictingFeedbacks
     * @param existingConflicts       conflicts with causingResult as the causing Result that curently exist in the database
     * @param newConflictingFeedbacks Map which contains existing feedbacks the causingResult is currently in conflict with. The feedbacks are mapped to the corresponding
     *                                modelElementId of the feedback from causingResult that is inside the same similarity set as the List of feedbacks and therefore conflicting.
     */
    @Transactional
    public void addMissingConflicts(Result causingResult, List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        newConflictingFeedbacks.keySet().forEach(modelElementId -> {
            Optional<ModelAssessmentConflict> foundExistingConflict = existingConflicts.stream()
                    .filter(existingConflict -> existingConflict.getCausingConflictingResult().getModelElementId().equals(modelElementId)).findFirst();
            if (foundExistingConflict.isEmpty()) {
                ModelAssessmentConflict newConflict = createConflict(modelElementId, causingResult, newConflictingFeedbacks.get(modelElementId));
                existingConflicts.add(newConflict);
            }
        });
    }

    /**
     * Resolves conflicts which no longer are in conflict with existing feedbacks represented by the newConflictingFeedbacks map. Updates the list resultsInConflicts of the
     * conflicts, that still have feedbacks they are in conflict with.
     *
     * @param existingConflicts       all conflicts of one causing result that curently exist in the database
     * @param newConflictingFeedbacks Map which contains existing feedbacks the causingResult is currently in conflict with. The feedbacks are mapped to the corresponding
     *                                modelElementId of the feedback from causingResult that is inside the same similarity set as the List of feedbacks and therefore conflicting.
     */
    @Transactional
    public void updateExistingConflicts(List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        existingConflicts.forEach(conflict -> {
            List<Feedback> newFeedbacks = newConflictingFeedbacks.get(conflict.getCausingConflictingResult().getModelElementId());
            if (newFeedbacks != null) {
                conflictingResultService.updateExistingConflictingResults(conflict, newFeedbacks);
            }
            else {
                resolveConflict(conflict);
            }
        });
    }

    /**
     * Checks if the current user is responsible for handling a model assessment conflict
     *
     * @param conflict The conflict for which the responsibility should be checked
     * @param exercise The exercise related to the given conflict
     * @return True, if the the user is the assessor of the unhandled conflict, or any conflicting results inside the conflict
     */
    private boolean currentUserIsResponsibleForHandling(ModelAssessmentConflict conflict, Exercise exercise) {
        User currentUser = userService.getUser();
        if (authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return true;
        }
        switch (conflict.getState()) {
        case UNHANDLED:
            return conflict.getCausingConflictingResult().getResult().getAssessor().equals(currentUser);
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            return conflict.getResultsInConflict().stream().anyMatch(conflictingResult -> conflictingResult.getResult().getAssessor().equals(currentUser));
        default:
            return false;
        }

    }

    private ModelAssessmentConflict createConflict(String causingModelElementId, Result causingResult, List<Feedback> feedbacksInConflict) {
        ModelAssessmentConflict conflict = new ModelAssessmentConflict();
        Set<ConflictingResult> resultsInConflict = new HashSet<>();
        feedbacksInConflict.forEach(feedback -> {
            ConflictingResult conflictingResult = conflictingResultService.createConflictingResult(conflict, feedback);
            resultsInConflict.add(conflictingResult);
        });
        ConflictingResult causingConflictingResult = conflictingResultService.createConflictingResult(causingModelElementId, causingResult);
        conflict.setCausingConflictingResult(causingConflictingResult);
        conflict.setResultsInConflict(resultsInConflict);
        conflict.setCreationDate(ZonedDateTime.now());
        conflict.setState(EscalationState.UNHANDLED);
        return conflict;
    }

    /**
     * Updates the state of the given conflict to resolved depending on the previous state of the conflict and sets the resolution date
     */
    private void resolveConflict(ModelAssessmentConflict conflict) {
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
