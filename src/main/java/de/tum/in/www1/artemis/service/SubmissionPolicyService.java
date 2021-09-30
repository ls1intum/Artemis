package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final Logger log = LoggerFactory.getLogger(SubmissionPolicyService.class);

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

    public SubmissionPolicy addSubmissionPolicyToProgrammingExercise(SubmissionPolicy submissionPolicy, ProgrammingExercise programmingExercise) {
        submissionPolicy.setActive(true);
        submissionPolicy.setProgrammingExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        SubmissionPolicy addedSubmissionPolicy = submissionPolicyRepository.save(submissionPolicy);
        programmingExercise.setSubmissionPolicy(addedSubmissionPolicy);
        enableSubmissionPolicy(addedSubmissionPolicy);
        return addedSubmissionPolicy;
    }

    public void validateSubmissionPolicyCreation(ProgrammingExercise programmingExercise) {
        SubmissionPolicy submissionPolicy = programmingExercise.getSubmissionPolicy();
        if (submissionPolicy == null) {
            return;
        }
        validateSubmissionPolicy(submissionPolicy);
        submissionPolicy.setActive(true);
        submissionPolicy = submissionPolicyRepository.save(submissionPolicy);
        submissionPolicy.setProgrammingExercise(programmingExercise);
        programmingExercise.setSubmissionPolicy(submissionPolicy);
    }

    public void validateSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        if (submissionPolicy instanceof LockRepositoryPolicy policy) {
            validateLockRepositoryPolicy(policy);
        }
        else if (submissionPolicy instanceof SubmissionPenaltyPolicy policy) {
            validateSubmissionPenaltyPolicy(policy);
        }
    }

    private void validateLockRepositoryPolicy(LockRepositoryPolicy lockRepositoryPolicy) {
        Integer submissionLimit = lockRepositoryPolicy.getSubmissionLimit();
        if (submissionLimit == null || submissionLimit < 1) {
            throw new BadRequestAlertException("The submission limit of submission policies must be greater than 0.", SubmissionPolicyResource.ENTITY_NAME,
                    "submissionPolicyIllegalSubmissionLimit");
        }
    }

    private void validateSubmissionPenaltyPolicy(SubmissionPenaltyPolicy submissionPenaltyPolicy) {
        Integer submissionLimit = submissionPenaltyPolicy.getSubmissionLimit();
        Double penalty = submissionPenaltyPolicy.getExceedingPenalty();
        if (submissionLimit == null || submissionLimit < 1) {
            throw new BadRequestAlertException("The submission limit of submission policies must be greater than 0.", SubmissionPolicyResource.ENTITY_NAME,
                    "submissionPolicyIllegalSubmissionLimit");
        }

        if (penalty == null || penalty <= 0) {
            throw new BadRequestAlertException("The penalty of submission penalty policies must be greater than 0.", SubmissionPolicyResource.ENTITY_NAME,
                    "submissionPenaltyPolicyIllegalPenalty");
        }

    }

    public void removeSubmissionPolicyFromProgrammingExercise(ProgrammingExercise programmingExercise) {
        disableSubmissionPolicy(programmingExercise.getSubmissionPolicy());
        programmingExercise.setSubmissionPolicy(null);
        programmingExerciseRepository.save(programmingExercise);
    }

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
        for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
            if (getParticipationSubmissionCount(studentParticipation) >= policy.getSubmissionLimit()) {
                programmingExerciseParticipationService.lockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
            }
        }
        policy.setActive(true);
        submissionPolicyRepository.save(policy);
    }

    private void enableSubmissionPenaltyPolicy(SubmissionPenaltyPolicy policy) {
        toggleSubmissionPolicy(policy, true);
    }

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
        for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
            if (getParticipationSubmissionCount(studentParticipation) >= policy.getSubmissionLimit()) {
                programmingExerciseParticipationService.unlockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
            }
        }
        toggleSubmissionPolicy(policy, false);
    }

    private void disableSubmissionPenaltyPolicy(SubmissionPenaltyPolicy policy) {
        toggleSubmissionPolicy(policy, false);
    }

    public void updateSubmissionPolicy(ProgrammingExercise exercise, SubmissionPolicy newPolicy) {
        SubmissionPolicy originalPolicy = exercise.getSubmissionPolicy();
        if (originalPolicy instanceof LockRepositoryPolicy) {
            updateLockRepositoryPolicy(originalPolicy, newPolicy);
        }
        else if (originalPolicy instanceof SubmissionPenaltyPolicy) {
            updateSubmissionPenaltyPolicy((SubmissionPenaltyPolicy) originalPolicy, (SubmissionPenaltyPolicy) newPolicy);
        }
        submissionPolicyRepository.save(originalPolicy);
    }

    private void updateLockRepositoryPolicy(SubmissionPolicy originalPolicy, SubmissionPolicy newPolicy) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(originalPolicy.getProgrammingExercise().getId());
        if (originalPolicy.getSubmissionLimit() < newPolicy.getSubmissionLimit()) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                if (getParticipationSubmissionCount(studentParticipation) >= originalPolicy.getSubmissionLimit()) {
                    programmingExerciseParticipationService.unlockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
                }
            }
        }
        else if (originalPolicy.getSubmissionLimit() > newPolicy.getSubmissionLimit()) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                if (getParticipationSubmissionCount(studentParticipation) >= newPolicy.getSubmissionLimit()) {
                    programmingExerciseParticipationService.lockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
                }
            }
        }
        originalPolicy.setSubmissionLimit(newPolicy.getSubmissionLimit());
    }

    private void updateSubmissionPenaltyPolicy(SubmissionPenaltyPolicy originalPolicy, SubmissionPenaltyPolicy newPolicy) {
        originalPolicy.setSubmissionLimit(newPolicy.getSubmissionLimit());
        originalPolicy.setExceedingPenalty(newPolicy.getExceedingPenalty());
    }

    /**
     * Calculates the achievable score for one programming exercise participation in [0;100]%
     * The achievable score depends on the presence of a submission penalty policy.
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

    public void handleLockRepositoryPolicy(Result result, LockRepositoryPolicy lockRepositoryPolicy) {
        if (lockRepositoryPolicy == null || !lockRepositoryPolicy.isActive()) {
            return;
        }
        int submissions = getParticipationSubmissionCount(result);
        int allowedSubmissions = lockRepositoryPolicy.getSubmissionLimit();
        if (submissions == allowedSubmissions) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(lockRepositoryPolicy.getProgrammingExercise().getId());
            programmingExerciseParticipationService.lockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) result.getParticipation());
        }
        // This is the fallback behavior in case the VCS does not lock the repository for whatever reason when the
        // submission limit is reached.
        else if (submissions > allowedSubmissions) {
            result.setRated(false);
        }
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

    public String calculateResultStringAttachment(ProgrammingExercise exercise, Participation participation) {
        SubmissionPolicy policy = exercise.getSubmissionPolicy();
        if (policy == null || !policy.isActive()) {
            return "";
        }
        int submissions = getParticipationSubmissionCount(participation);
        int allowedSubmissions = policy.getSubmissionLimit();
        if (submissions <= allowedSubmissions) {
            return ", %d of %d Submissions".formatted(submissions, allowedSubmissions);
        } else if (policy instanceof SubmissionPenaltyPolicy submissionPenaltyPolicy) {
            return ", %d%% Submission Penalty".formatted((int) Math.min(submissionPenaltyPolicy.getExceedingPenalty() * (submissions - allowedSubmissions), 100));
        }
        return "";
    }
}
