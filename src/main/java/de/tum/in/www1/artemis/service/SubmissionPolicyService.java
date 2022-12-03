package de.tum.in.www1.artemis.service;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.web.rest.SubmissionPolicyResource;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class SubmissionPolicyService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ParticipationRepository participationRepository;

    public SubmissionPolicyService(ProgrammingExerciseRepository programmingExerciseRepository, SubmissionPolicyRepository submissionPolicyRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ParticipationRepository participationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.participationRepository = participationRepository;
    }

    /**
     * Adds the submission policy to the programming exercise and saves both entities.
     * The submission policy must be set as either active or inactive by the client.
     * Currently, the client always sends inactive policies to prevent unwanted effects
     * on participations.
     *
     * @param submissionPolicy that should be added to the programming exercise
     * @param programmingExercise that the submission policy is added to
     * @return the persisted submission policy object that is associated with the programming exercise
     */
    public SubmissionPolicy addSubmissionPolicyToProgrammingExercise(SubmissionPolicy submissionPolicy, ProgrammingExercise programmingExercise) {
        SubmissionPolicy addedSubmissionPolicy = submissionPolicyRepository.save(submissionPolicy);
        programmingExercise.setSubmissionPolicy(addedSubmissionPolicy);
        programmingExerciseRepository.save(programmingExercise);
        return addedSubmissionPolicy;
    }

    /**
     * Validates the submission policy of a newly created exercise if it exists.
     * This is only called, when the client posts a new programming exercise either to
     * the regular programming exercise creation endpoint or the programming exercise simulation creation endpoint.
     * In this case, the submission policy is activated by default.
     *
     * @param programmingExercise that contains the submission policy that is to be checked
     */
    public void validateSubmissionPolicyCreation(ProgrammingExercise programmingExercise) {
        SubmissionPolicy submissionPolicy = programmingExercise.getSubmissionPolicy();
        if (submissionPolicy == null) {
            return;
        }
        submissionPolicy.setActive(true);
        validateSubmissionPolicy(submissionPolicy);
        submissionPolicy = submissionPolicyRepository.save(submissionPolicy);
        submissionPolicy.setProgrammingExercise(programmingExercise);
        programmingExercise.setSubmissionPolicy(submissionPolicy);
    }

    /**
     * Validates the submission policy. This check ensures that a submission policy is
     * either active or inactive and has a positive submission limit. Depending on the
     * type of submission policy, individual checks are applied:
     * <ol>
     *     <li>Lock Repository Policy: No additional checks</li>
     *     <li>Submission Penalty Policy: Ensures that the penalty is greater than 0.</li>
     * </ol>
     *
     * @param submissionPolicy that should be validated
     */
    public void validateSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        Integer submissionLimit = submissionPolicy.getSubmissionLimit();
        if (submissionPolicy.isActive() == null) {
            throw new BadRequestAlertException("Submission policies must be activated or deactivated. Activation cannot be null.", SubmissionPolicyResource.ENTITY_NAME,
                    "submissionPolicyActiveNull");
        }
        if (submissionLimit == null || submissionLimit < 1) {
            throw new BadRequestAlertException("The submission limit of submission policies must be greater than 0.", SubmissionPolicyResource.ENTITY_NAME,
                    "submissionPolicyIllegalSubmissionLimit");
        }
        // Currently, only submission penalty policies must be validated further.
        // A validateLockRepositoryPolicy method would be empty.
        if (submissionPolicy instanceof SubmissionPenaltyPolicy policy) {
            validateSubmissionPenaltyPolicy(policy);
        }
    }

    private void validateSubmissionPenaltyPolicy(SubmissionPenaltyPolicy submissionPenaltyPolicy) {
        Double penalty = submissionPenaltyPolicy.getExceedingPenalty();
        if (penalty == null || penalty <= 0) {
            throw new BadRequestAlertException("The penalty of submission penalty policies must be greater than 0.", SubmissionPolicyResource.ENTITY_NAME,
                    "submissionPenaltyPolicyIllegalPenalty");
        }

    }

    /**
     * Removes the submission policy of a programming exercise. Before the policy is removed,
     * the effect on participations is removed.
     *
     * @param programmingExercise for which the submission policy should be removed
     */
    public void removeSubmissionPolicyFromProgrammingExercise(ProgrammingExercise programmingExercise) {
        disableSubmissionPolicy(programmingExercise.getSubmissionPolicy());
        programmingExercise.setSubmissionPolicy(null);
        programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Enables the submission policy. This applies the effect of the submission policy retroactively to
     * all participations, depending on the type of policy.
     *
     * @param policy that should be enabled
     * @return the enabled and persisted submission policy
     */
    public SubmissionPolicy enableSubmissionPolicy(SubmissionPolicy policy) {
        if (policy instanceof LockRepositoryPolicy lockRepositoryPolicy) {
            return enableLockRepositoryPolicy(lockRepositoryPolicy);
        }
        else if (policy instanceof SubmissionPenaltyPolicy submissionPenaltyPolicy) {
            return enableSubmissionPenaltyPolicy(submissionPenaltyPolicy);
        }
        else {
            throw new NotImplementedException();
        }
    }

    private SubmissionPolicy enableLockRepositoryPolicy(LockRepositoryPolicy policy) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(policy.getProgrammingExercise().getId());
        lockParticipationsWhenSubmissionsGreaterLimit(exercise, policy.getSubmissionLimit());
        policy.setActive(true);
        return submissionPolicyRepository.save(policy);
    }

    private SubmissionPolicy enableSubmissionPenaltyPolicy(SubmissionPenaltyPolicy policy) {
        return toggleSubmissionPolicy(policy, true);
    }

    /**
     * Disables the submission policy. This removes the effect of the submission policy retroactively from
     * all participations, depending on the type of policy.
     *
     * @param policy that should be enabled
     */
    public void disableSubmissionPolicy(SubmissionPolicy policy) {
        if (policy instanceof LockRepositoryPolicy lockRepositoryPolicy) {
            disableLockRepositoryPolicy(lockRepositoryPolicy);
        }
        else if (policy instanceof SubmissionPenaltyPolicy submissionPenaltyPolicy) {
            disableSubmissionPenaltyPolicy(submissionPenaltyPolicy);
        }
        else {
            throw new NotImplementedException();
        }
    }

    private void disableLockRepositoryPolicy(LockRepositoryPolicy policy) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(policy.getProgrammingExercise().getId());
        unlockParticipationsWhenSubmissionsGreaterLimit(exercise, policy.getSubmissionLimit());
        toggleSubmissionPolicy(policy, false);
    }

    private void disableSubmissionPenaltyPolicy(SubmissionPenaltyPolicy policy) {
        toggleSubmissionPolicy(policy, false);
    }

    /**
     * Updates the existing submission policy of a programming exercise with new values.
     * When a submission policy is updated, the effect on participations is updated as well.
     * The effect of submission penalty policies is NOT updated automatically. The user needs
     * to re-evaluate all results for the policy to take action.
     * <br>
     * Example:
     * When updating a lock repository policy from 5 allowed submissions to 10 allowed submissions,
     * every participation repository with 5 submissions is unlocked.
     *
     * @param programmingExercise for which the submission policy should be changed
     * @param newPolicy with updates attribute values
     * @return the updated submission policy
     */
    public SubmissionPolicy updateSubmissionPolicy(ProgrammingExercise programmingExercise, SubmissionPolicy newPolicy) {
        SubmissionPolicy originalPolicy = programmingExercise.getSubmissionPolicy();

        // Case 1: The original and new submission policies are both lock repository policies. Then we can simply
        // update the existing policy with the new values and handle repository (un)locks
        if (originalPolicy instanceof LockRepositoryPolicy && newPolicy instanceof LockRepositoryPolicy) {
            updateLockRepositoryPolicy(originalPolicy, newPolicy);
        }

        // Case 2: The original and new submission policies are both submission penalty policies. Then we can simply
        // update the existing policy.
        else if (originalPolicy instanceof SubmissionPenaltyPolicy && newPolicy instanceof SubmissionPenaltyPolicy) {
            updateSubmissionPenaltyPolicy((SubmissionPenaltyPolicy) originalPolicy, (SubmissionPenaltyPolicy) newPolicy);
        }

        // Case 3: The original and new submission policies have different types. In this case we want to remove
        // all effects of the original policy and enforce the effects of the new policy.
        else {
            removeSubmissionPolicyFromProgrammingExercise(programmingExercise);
            newPolicy = addSubmissionPolicyToProgrammingExercise(newPolicy, programmingExercise);
            return enableSubmissionPolicy(newPolicy);
        }
        return submissionPolicyRepository.save(originalPolicy);
    }

    private void updateLockRepositoryPolicy(SubmissionPolicy originalPolicy, SubmissionPolicy newPolicy) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(originalPolicy.getProgrammingExercise().getId());
        if (originalPolicy.getSubmissionLimit() < newPolicy.getSubmissionLimit()) {
            unlockParticipationsWhenSubmissionsGreaterLimit(exercise, originalPolicy.getSubmissionLimit());
        }
        else if (originalPolicy.getSubmissionLimit() > newPolicy.getSubmissionLimit()) {
            lockParticipationsWhenSubmissionsGreaterLimit(exercise, newPolicy.getSubmissionLimit());
        }
        originalPolicy.setSubmissionLimit(newPolicy.getSubmissionLimit());
    }

    private void lockParticipationsWhenSubmissionsGreaterLimit(ProgrammingExercise exercise, int submissionLimit) {
        for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
            if (getParticipationSubmissionCount(studentParticipation) >= submissionLimit) {
                programmingExerciseParticipationService.lockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
            }
        }
    }

    private void unlockParticipationsWhenSubmissionsGreaterLimit(ProgrammingExercise exercise, int submissionLimit) {
        for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
            if (getParticipationSubmissionCount(studentParticipation) >= submissionLimit) {
                programmingExerciseParticipationService.unlockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
            }
        }
    }

    private void updateSubmissionPenaltyPolicy(SubmissionPenaltyPolicy originalPolicy, SubmissionPenaltyPolicy newPolicy) {
        originalPolicy.setSubmissionLimit(newPolicy.getSubmissionLimit());
        originalPolicy.setExceedingPenalty(newPolicy.getExceedingPenalty());
    }

    /**
     * Calculates the deduction for one programming exercise participation.
     *
     * @param participation for which the deduction should be determined
     * @param submissionPenaltyPolicy that contains the policy configuration of the programming exercise
     * @return total deduction in absolute points
     */
    public double calculateSubmissionPenalty(Participation participation, SubmissionPenaltyPolicy submissionPenaltyPolicy) {
        if (submissionPenaltyPolicy != null && submissionPenaltyPolicy.isActive()) {
            int presentSubmissions = getParticipationSubmissionCount(participation);
            int illegalSubmissionCount = presentSubmissions - submissionPenaltyPolicy.getSubmissionLimit();
            if (illegalSubmissionCount > 0) {
                return illegalSubmissionCount * submissionPenaltyPolicy.getExceedingPenalty();
            }
        }
        return 0;
    }

    /**
     * Checks whether the participation repository should be locked for a new incoming result.
     * The repository is locked, when the new number of submissions matches the amount of allowed
     * submissions specified in the lock repository policy. When the new number of submissions is
     * greater than the allowed submissions, the result will not be included in the participation
     * score.
     *
     * @param result that is coming in from a build result
     * @param participation that the result will belong to
     * @param lockRepositoryPolicy defining the number of allowed submissions for this exercise
     */
    public void handleLockRepositoryPolicy(Result result, Participation participation, LockRepositoryPolicy lockRepositoryPolicy) {
        if (lockRepositoryPolicy == null || !lockRepositoryPolicy.isActive()) {
            return;
        }
        int submissions = getParticipationSubmissionCount(participation);
        int allowedSubmissions = lockRepositoryPolicy.getSubmissionLimit();
        if (submissions == allowedSubmissions) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository
                    .findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(lockRepositoryPolicy.getProgrammingExercise().getId());
            programmingExerciseParticipationService.lockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) result.getParticipation());
        }
        // This is the fallback behavior in case the VCS does not lock the repository for whatever reason when the
        // submission limit is reached.
        else if (submissions > allowedSubmissions) {
            result.setRated(false);
        }
    }

    /**
     * Calculates and returns the number of submissions for one participation. This amount represents
     * the amount of unique manual submissions with at least one result.
     *
     * @param participation for which the number of submissions should be determined
     * @return the number of submissions of this participation
     */
    private int getParticipationSubmissionCount(Participation participation) {
        final Long participationId = participation.getId();
        int submissionCompensation = 0;
        participation = participationRepository.findByIdWithLatestSubmissionAndResult(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
        var submissions = participation.getSubmissions();
        if (submissions != null && !submissions.isEmpty()) {
            submissionCompensation = submissions.iterator().next().getResults().isEmpty() ? 1 : 0;
        }
        return (int) programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId).stream()
                .filter(submission -> submission.getType() == SubmissionType.MANUAL && !submission.getResults().isEmpty()).map(ProgrammingSubmission::getCommitHash).distinct()
                .count() + submissionCompensation;
    }

    private SubmissionPolicy toggleSubmissionPolicy(SubmissionPolicy policy, boolean active) {
        policy.setActive(active);
        return submissionPolicyRepository.save(policy);
    }

    /**
     * Generates and adds Feedback to the result if the Submission Penalty Policy is enforced.
     * The added feedback is negative and has negative credits depending on the number of
     * submissions exceeding the submission limit.
     *
     * @param result to which the feedback item should be added
     * @param penaltyPolicy that specifies the submission limit and penalty
     */
    public void createFeedbackForPenaltyPolicy(Result result, SubmissionPenaltyPolicy penaltyPolicy) {
        if (penaltyPolicy != null && penaltyPolicy.isActive()) {
            int presentSubmissions = getParticipationSubmissionCount(result.getParticipation());
            int illegalSubmissionCount = presentSubmissions - penaltyPolicy.getSubmissionLimit();
            if (illegalSubmissionCount > 0) {
                double deduction = illegalSubmissionCount * penaltyPolicy.getExceedingPenalty();
                Feedback penaltyFeedback = new Feedback().credits(-deduction).text(Feedback.SUBMISSION_POLICY_FEEDBACK_IDENTIFIER + "Submission Penalty Policy")
                        .detailText("You have submitted %d more time%s than the submission limit of %d. This results in a deduction of %.1f points!"
                                .formatted(illegalSubmissionCount, illegalSubmissionCount == 1 ? "" : "s", penaltyPolicy.getSubmissionLimit(), deduction))
                        .positive(false).type(FeedbackType.AUTOMATIC).result(result);
                result.addFeedback(penaltyFeedback);
            }
        }
    }

    /**
     * Determines whether a participation repository is locked, depending on the active policy
     * of a programming exercise. This method does NOT take any other factors into account.
     *
     * @param policy that determines the submission limit for the programming exercise
     * @param programmingParticipation that is either locked or unlocked
     * @return true when the repository should be locked, false if not
     */
    public boolean isParticipationLocked(LockRepositoryPolicy policy, Participation programmingParticipation) {
        if (policy == null || !policy.isActive()) {
            return false;
        }
        return policy.getSubmissionLimit() <= getParticipationSubmissionCount(programmingParticipation);
    }
}
