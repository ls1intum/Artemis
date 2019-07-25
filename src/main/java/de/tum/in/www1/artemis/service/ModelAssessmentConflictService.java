package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.repository.ConflictingResultRepository;
import de.tum.in.www1.artemis.repository.ModelAssessmentConflictRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(ModelAssessmentConflictService.class);

    private final ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    private final ConflictingResultService conflictingResultService;

    private final ConflictingResultRepository conflictingResultRepository;

    @Autowired
    private CompassService compassService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResultRepository resultRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final GroupNotificationService groupNotificationService;

    public ModelAssessmentConflictService(ModelAssessmentConflictRepository modelAssessmentConflictRepository, ConflictingResultService conflictingResultService,
            ConflictingResultRepository conflictingResultRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            SingleUserNotificationService singleUserNotificationService, GroupNotificationService groupNotificationService) {
        this.modelAssessmentConflictRepository = modelAssessmentConflictRepository;
        this.conflictingResultService = conflictingResultService;
        this.conflictingResultRepository = conflictingResultRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.groupNotificationService = groupNotificationService;
    }

    public ModelAssessmentConflict findOne(Long conflictId) {
        return modelAssessmentConflictRepository.findById(conflictId).orElseThrow(() -> new EntityNotFoundException("Entity with id " + conflictId + "does not exist"));
    }

    public List<ModelAssessmentConflict> getConflictsForExercise(Long exerciseId) {
        return modelAssessmentConflictRepository.findAllConflictsOfExercise(exerciseId);
    }

    @Transactional(readOnly = true)
    public List<ModelAssessmentConflict> getConflictsForCurrentUserForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> conflictsForSubmission = getConflictsForSubmission(submissionId);
        User currentUser = userService.getUser();
        if (conflictsForSubmission.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        else {
            Exercise exercise = conflictsForSubmission.get(0).getCausingConflictingResult().getResult().getParticipation().getExercise();
            return conflictsForSubmission.stream().filter(conflict -> userIsResponsibleForHandling(conflict, exercise, currentUser)).collect(Collectors.toList());
        }
    }

    public List<ModelAssessmentConflict> getConflictsForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingSubmission(submissionId);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(existingConflicts);
        return existingConflicts;
    }

    public List<ModelAssessmentConflict> getConflictsForResult(Result result) {
        List<ModelAssessmentConflict> conflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        return conflicts;
    }

    public List<ModelAssessmentConflict> getConflictsForResultInConflict(Result result) {
        List<ModelAssessmentConflict> conflicts = modelAssessmentConflictRepository.findAllConflictsByResultInConflict(result);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(conflicts);
        return conflicts;
    }

    public List<ModelAssessmentConflict> getUnresolvedConflictsForResult(Result result) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsForResult(result);
        return existingConflicts.stream().filter(conflict -> !conflict.isResolved()).collect(Collectors.toList());
    }

    public List<ModelAssessmentConflict> getConflictsForResultWithState(Result result, EscalationState state) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsForResult(result);
        return existingConflicts.stream().filter(conflict -> conflict.getState().equals(state)).collect(Collectors.toList());
    }

    // TODO MJ JavaDoc
    public void updateConflictsOnResultRemoval(Result result) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        modelAssessmentConflictRepository.deleteAll(existingConflicts);
        existingConflicts = modelAssessmentConflictRepository.findAllConflictsByResultInConflict(result);
        existingConflicts.forEach(conflict -> conflict.getResultsInConflict().forEach(conflictingResult -> {
            if (conflictingResult.getResult().getId().equals(result.getId())) {
                conflict.getResultsInConflict().remove(conflictingResult);
                conflictingResultRepository.deleteById(conflictingResult.getId());
                System.out.println("test");
            }
        }));
    }

    @Transactional
    public Exercise getExerciseOfConflict(Long conflictId) {
        ModelAssessmentConflict conflict = findOne(conflictId);
        return conflict.getCausingConflictingResult().getResult().getParticipation().getExercise();
    }

    /**
     * Loads properties of the given conflicts that are needed by the conflict resolution view of the client
     *
     * @param conflicts
     */
    public void loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(List<ModelAssessmentConflict> conflicts) {
        conflicts.forEach(this::loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults);
    }

    public void loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(ModelAssessmentConflict conflict) {
        conflict.getCausingConflictingResult()
                .setResult(resultRepository.findByIdWithEagerSubmissionAndFeedbacksAndAssessor(conflict.getCausingConflictingResult().getResult().getId()).get());
        conflict.getResultsInConflict().forEach(
                conflictingResult -> conflictingResult.setResult(resultRepository.findByIdWithEagerSubmissionAndFeedbacksAndAssessor(conflictingResult.getResult().getId()).get()));
    }

    public void saveConflicts(List<ModelAssessmentConflict> conflicts) {
        modelAssessmentConflictRepository.saveAll(conflicts);
    }

    @Transactional
    public void resolveConflictByInstructor(ModelAssessmentConflict conflict, Feedback decision) {
        verifyNotResolved(conflict);
        compassService.applyUpdateOnSubmittedAssessment(conflict.getCausingConflictingResult().getResult(), decision);
        conflict.getResultsInConflict().forEach(conflictingResult -> compassService.applyUpdateOnSubmittedAssessment(conflictingResult.getResult(), decision));
        conflict.setState(EscalationState.RESOLVED_BY_INSTRUCTOR);
        conflict.setResolutionDate(ZonedDateTime.now());
    }

    /**
     * Updates the state of the given conflict by escalating the conflict to the next authority. The assessors or instructors then responsible for handling the conflict are getting
     * notified.
     *
     * @param conflictId id of the conflict to escalate
     * @return escalated conflict of the given conflictId
     */
    @Transactional
    public ModelAssessmentConflict escalateConflict(ModelAssessmentConflict storedConflict) {
        verifyNotResolved(storedConflict);
        switch (storedConflict.getState()) {
        case UNHANDLED:
            Set<Result> distinctResultsInConflict = new HashSet<>();
            storedConflict.getResultsInConflict().forEach(conflictingResult -> distinctResultsInConflict.add(conflictingResult.getResult()));
            distinctResultsInConflict.forEach(result -> singleUserNotificationService.notifyTutorAboutNewConflictForResult(result,
                    storedConflict.getCausingConflictingResult().getResult().getParticipation().getExercise(),
                    storedConflict.getCausingConflictingResult().getResult().getAssessor()));
            storedConflict.setState(EscalationState.ESCALATED_TO_TUTORS_IN_CONFLICT);
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            groupNotificationService.notifyInstructorGroupAboutEscalatedConflict(storedConflict);
            storedConflict.setState(EscalationState.ESCALATED_TO_INSTRUCTOR);
            break;
        default:
            log.error("Escalating conflict {} with state {} failed .", storedConflict.getId(), storedConflict.getState());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict: " + storedConflict.getId() + " can´t be escalated");
        }
        modelAssessmentConflictRepository.save(storedConflict);
        return storedConflict;
    }

    /**
     * Used to update the list of conflicts according to the decisions of the currentUser to whom the list of conflicts got escalated to.
     *
     * @param conflicts
     * @param currentUser
     */
    @Transactional
    public void updateEscalatedConflicts(List<ModelAssessmentConflict> conflicts, User currentUser) {
        conflicts.forEach(conflict -> {
            ModelAssessmentConflict storedConflict = findOne(conflict.getId());
            ConflictingResult updatedConflictingResult = conflict.getResultsInConflict().stream()
                    .filter(conflictingResult -> conflictingResult.getResult().getAssessor().getId().equals(currentUser.getId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Provided request body contains conflict " + conflict.getId() + " without conflictingResult of user"));
            updateEscalatedConflict(storedConflict, updatedConflictingResult);
        });
    }

    /**
     * Adds new conflicts to the provided existingConflicts that are currently not present in the existingConflicts list but contained in the newConflictingFeedbacks mapping
     *
     * @param causingResult           Result that caused the conflicts in newConflictingFeedbacks
     * @param existingConflicts       conflicts with causingResult as the causing Result that curently exist in the database
     * @param newConflictingFeedbacks Map which contains existing feedbacks the causingResult is currently in conflict with. The feedbacks are mapped to the corresponding
     *                                modelElementId of the feedback from causingResult that is inside the same similarity set as the List of feedbacks and therefore conflicting.
     */
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
                resolveConflictByTutor(conflict);
            }
        });
    }

    public boolean userIsResponsibleForHandling(ModelAssessmentConflict conflict, Exercise exercise, User user) {
        if (authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return true;
        }
        switch (conflict.getState()) {
        case UNHANDLED:
            return conflict.getCausingConflictingResult().getResult().getAssessor().equals(user);
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            return conflict.getResultsInConflict().stream().anyMatch(conflictingResult -> conflictingResult.getResult().getAssessor().equals(user));
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
    private void resolveConflictByTutor(ModelAssessmentConflict conflict) {
        switch (conflict.getState()) {
        case UNHANDLED:
            conflict.setState(EscalationState.RESOLVED_BY_CAUSER);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            applyTutorsDecisionToCompass(conflict);
            conflict.setState(EscalationState.RESOLVED_BY_OTHER_TUTORS);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        default:
            log.error("Failed to resolve conflict {}. Illegal escalation state", conflict);
            break;
        }
    }

    /**
     * Updates the given storedConflict with the decision of a tutor to whom the storedConflict got escalated to. When all tutors posted their decision the conflict is either
     * escalated to an instructor or resolved in case all tutors decided the same way
     *
     * @param storedConflict
     * @param updatedConflictingResult
     */
    private void updateEscalatedConflict(ModelAssessmentConflict storedConflict, ConflictingResult updatedConflictingResult) {
        ConflictingResult storedConflictingResult = storedConflict.getResultsInConflict().stream()
                .filter(conflictingResult -> conflictingResult.getId().equals(updatedConflictingResult.getId())).findFirst().get();
        storedConflictingResult.setUpdatedFeedback(updatedConflictingResult.getUpdatedFeedback());
        if (decisionOfAllTutorsPresent(storedConflict)) {
            if (allTutorsAcceptedConflictCausingFeedback(storedConflict)) {
                resolveConflictByTutor(storedConflict);
            }
            else {
                escalateConflict(storedConflict);
            }
        }
        modelAssessmentConflictRepository.save(storedConflict);
    }

    private void applyTutorsDecisionToCompass(ModelAssessmentConflict conflict) {
        conflict.getResultsInConflict().forEach(conflictingResult -> {
            compassService.applyUpdateOnSubmittedAssessment(conflictingResult.getResult(), conflictingResult.getUpdatedFeedback());
        });
    }

    private boolean allTutorsAcceptedConflictCausingFeedback(ModelAssessmentConflict conflict) {
        ConflictingResult firstConflictingResult = conflict.getResultsInConflict().iterator().next();
        boolean tutorsDecisionUniform = conflict.getResultsInConflict().stream()
                .allMatch(cR -> cR.getUpdatedFeedback().getCredits().equals(firstConflictingResult.getUpdatedFeedback().getCredits()));
        if (tutorsDecisionUniform) {
            Feedback causingFeedback = conflict.getCausingConflictingResult().getResult().getFeedbacks().stream()
                    .filter(feedback -> feedback.getReferenceElementId().equals(conflict.getCausingConflictingResult().getModelElementId())).findFirst().get();
            return causingFeedback.getCredits().equals(firstConflictingResult.getUpdatedFeedback().getCredits());
        }
        else {
            return false;
        }
    }

    private boolean decisionOfAllTutorsPresent(ModelAssessmentConflict conflict) {
        return conflict.getResultsInConflict().stream().anyMatch(conflictingResult -> conflictingResult.getUpdatedFeedback() != null);
    }

    private void verifyNotResolved(ModelAssessmentConflict conflict) {
        if (conflict.isResolved()) {
            log.error("Escalating resolved conflict {} is not possible.", conflict.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict with id" + conflict.getId() + "has already been resolved");
        }
    }
}
