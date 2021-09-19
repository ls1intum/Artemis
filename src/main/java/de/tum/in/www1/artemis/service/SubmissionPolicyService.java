package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionPolicyRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.web.rest.SubmissionPolicyResource;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SubmissionPolicyService {

    private final Logger log = LoggerFactory.getLogger(SubmissionPolicyService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public SubmissionPolicyService(ProgrammingExerciseRepository programmingExerciseRepository, SubmissionPolicyRepository submissionPolicyRepository, ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    public void validateSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        if (submissionPolicy instanceof LockRepositoryPolicy policy) {
            validateLockRepositoryPolicy(policy);
        } else if (submissionPolicy instanceof SubmissionPenaltyPolicy policy) {
            validateSubmissionPenaltyPolicy(policy);
        }
    }

    public void validateLockRepositoryPolicy(LockRepositoryPolicy lockRepositoryPolicy) {
        Integer submissionLimit = lockRepositoryPolicy.getSubmissionLimit();
        if (submissionLimit == null || submissionLimit < 1) {
            throw new BadRequestAlertException("The submission limit of submission policies must be greater than 0.",
                SubmissionPolicyResource.ENTITY_NAME, "submissionPolicyIllegalSubmissionLimit");
        }
    }

    public void validateSubmissionPenaltyPolicy(SubmissionPenaltyPolicy submissionPenaltyPolicy) {
        Integer submissionLimit = submissionPenaltyPolicy.getSubmissionLimit();
        Double penalty = submissionPenaltyPolicy.getExceedingPenalty();
        if (submissionLimit == null || submissionLimit < 1) {
            throw new BadRequestAlertException("The submission limit of submission policies must be greater than 0.",
                SubmissionPolicyResource.ENTITY_NAME, "submissionPolicyIllegalSubmissionLimit");
        }

        if (penalty == null || penalty <= 0) {
            throw new BadRequestAlertException("The penalty of submission penalty policies must be greater than 0.",
                SubmissionPolicyResource.ENTITY_NAME, "submissionPenaltyPolicyIllegalPenalty");
        }

    }

    public SubmissionPolicy addSubmissionPolicyToProgrammingExercise(SubmissionPolicy submissionPolicy, ProgrammingExercise programmingExercise) {
        programmingExercise.setSubmissionPolicy(submissionPolicy);
        submissionPolicy.setProgrammingExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        return submissionPolicyRepository.save(submissionPolicy);
    }

    public void removeSubmissionPolicyFromProgrammingExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.setSubmissionPolicy(null);
        programmingExerciseRepository.save(programmingExercise);
    }

    public void handleLockRepositoryPolicyChange(SubmissionPolicy originalPolicy, SubmissionPolicy newPolicy) {
        ProgrammingExercise exercise = originalPolicy.getProgrammingExercise();
        if (originalPolicy.getSubmissionLimit() < newPolicy.getSubmissionLimit()) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                if (studentParticipation.getResults().size() >= originalPolicy.getSubmissionLimit()) {
                    programmingExerciseParticipationService.unlockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
                }
            }
        } else if (originalPolicy.getSubmissionLimit() > newPolicy.getSubmissionLimit()) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                if (studentParticipation.getResults().size() >= newPolicy.getSubmissionLimit()) {
                    programmingExerciseParticipationService.lockStudentRepository(exercise, (ProgrammingExerciseStudentParticipation) studentParticipation);
                }
            }
        }
    }
}
