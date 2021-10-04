package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SubmissionPolicyRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.web.rest.SubmissionPolicyResource;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class SubmissionPolicyService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    public SubmissionPolicyService(ProgrammingExerciseRepository programmingExerciseRepository, SubmissionPolicyRepository submissionPolicyRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
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
        submissionPolicy.setProgrammingExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
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
     */
    public void enableSubmissionPolicy(SubmissionPolicy policy) {
        if (policy instanceof LockRepositoryPolicy lockRepositoryPolicy) {
            enableLockRepositoryPolicy(lockRepositoryPolicy);
        }
        else if (policy instanceof SubmissionPenaltyPolicy submissionPenaltyPolicy) {
            enableSubmissionPenaltyPolicy(submissionPenaltyPolicy);
        }
    }

    private void enableLockRepositoryPolicy(LockRepositoryPolicy policy) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(policy.getProgrammingExercise().getId());
        lockParticipationsWhenSubmissionsGreaterLimit(exercise, policy.getSubmissionLimit());
        policy.setActive(true);
        submissionPolicyRepository.save(policy);
    }

    private void enableSubmissionPenaltyPolicy(SubmissionPenaltyPolicy policy) {
        toggleSubmissionPolicy(policy, true);
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
     * The type of submission policy must not change. When a submission policy is updated,
     * the effect on participations is updated as well.
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
        if (originalPolicy instanceof LockRepositoryPolicy) {
            updateLockRepositoryPolicy(originalPolicy, newPolicy);
        }
        else if (originalPolicy instanceof SubmissionPenaltyPolicy) {
            updateSubmissionPenaltyPolicy((SubmissionPenaltyPolicy) originalPolicy, (SubmissionPenaltyPolicy) newPolicy);
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
     * Calculates the achievable score for one programming exercise participation in [0;100]%
     * The achievable score depends on the presence of a submission penalty policy.
     *
     * @param participation for which the achievable score should be determined
     * @param submissionPenaltyPolicy that contains the policy configuration of the programming exercise
     * @return achievable score in %
     */
    public double calculateAchievableScoreForParticipation(Participation participation, SubmissionPenaltyPolicy submissionPenaltyPolicy) {
        if (submissionPenaltyPolicy != null && submissionPenaltyPolicy.isActive()) {
            int presentSubmissions = getParticipationSubmissionCount(participation);
            int illegalSubmissionCount = presentSubmissions - submissionPenaltyPolicy.getSubmissionLimit();
            if (illegalSubmissionCount > 0) {
                return Math.max(100 - (illegalSubmissionCount * submissionPenaltyPolicy.getExceedingPenalty()), 0);
            }
        }
        return 100;
    }

    /**
     * Checks whether the participation repository should be locked for a new incoming result.
     * The repository is locked, when the new number of submissions matches the amount of allowed
     * submissions specified in the lock repository policy. When the new number of submissions is
     * greater than the allowed submissions, the result will not be included in the participation
     * score.
     *
     * @param result that is coming in from a build result
     * @param lockRepositoryPolicy defining the number of allowed submissions for this exercise
     */
    public void handleLockRepositoryPolicy(Result result, LockRepositoryPolicy lockRepositoryPolicy) {
        if (lockRepositoryPolicy == null || !lockRepositoryPolicy.isActive()) {
            return;
        }
        int submissions = getParticipationSubmissionCount(result);
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
     * Determines the string that is attached to the result string of an incoming result.
     * When the number of submissions is below the allowed submissions specified in the submission policy
     * of the exercise, the attachment has the form 'x of y Submissions', where x is the number
     * of submissions and y is the allowed number of submissions. <br>
     * When a submission penalty policy is active and the submission count exceeds the allowed limit,
     * the attachment has the form 'z% Submission Penalty' where z is the imposed score penalty.
     *
     * @param exercise that specifies the active submission policy
     * @param participation to which the new result belongs
     * @return the attachment to the result string
     */
    public String calculateResultStringAttachment(ProgrammingExercise exercise, Participation participation) {
        SubmissionPolicy policy = exercise.getSubmissionPolicy();
        if (policy == null || !policy.isActive()) {
            return "";
        }
        int submissions = getParticipationSubmissionCount(participation);
        int allowedSubmissions = policy.getSubmissionLimit();
        if (submissions <= allowedSubmissions) {
            return ", %d of %d Submissions".formatted(submissions, allowedSubmissions);
        }
        else if (policy instanceof SubmissionPenaltyPolicy submissionPenaltyPolicy) {
            return ", %d%% Submission Penalty".formatted((int) Math.min(submissionPenaltyPolicy.getExceedingPenalty() * (submissions - allowedSubmissions), 100));
        }
        return "";
    }

    private int getParticipationSubmissionCount(Result result) {
        return getParticipationSubmissionCount(result.getParticipation());
    }

    private int getParticipationSubmissionCount(Participation participation) {
        return (int) programmingSubmissionRepository.findAllByParticipationIdWithResults(participation.getId()).stream()
                .filter(submission -> submission.getType() == SubmissionType.MANUAL).map(ProgrammingSubmission::getCommitHash).distinct().count();
    }

    private void toggleSubmissionPolicy(SubmissionPolicy policy, boolean active) {
        policy.setActive(active);
        submissionPolicyRepository.save(policy);
    }
}
