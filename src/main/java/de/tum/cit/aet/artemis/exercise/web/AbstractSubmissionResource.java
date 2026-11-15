package de.tum.cit.aet.artemis.exercise.web;

import java.util.List;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;

/**
 * Abstract class that allows reuse
 */
public abstract class AbstractSubmissionResource {

    protected final SubmissionRepository submissionRepository;

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final SubmissionService submissionService;

    protected final StudentParticipationRepository studentParticipationRepository;

    public AbstractSubmissionResource(SubmissionRepository submissionRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ExerciseRepository exerciseRepository, SubmissionService submissionService, StudentParticipationRepository studentParticipationRepository) {
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.submissionService = submissionService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Loads submissions for a list endpoint, excluding exam test runs and course Athena results for the self selection.
     * Endpoint annotations enforce access; the endpoint's DTO projection determines participant visibility.
     *
     * @param exerciseId      the exercise to load submissions for
     * @param submittedOnly   whether to select only submitted submissions in the all-submissions selection
     * @param assessedByMe    whether to select only the current user's assessments
     * @param correctionRound the correction round for the self selection
     * @return the submissions for the requested selection
     */
    protected List<Submission> getSubmissionsForList(Long exerciseId, boolean submittedOnly, boolean assessedByMe, int correctionRound) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        final boolean examMode = exercise.isExamExercise();
        List<Submission> submissions;
        if (assessedByMe) {
            User user = userRepository.getUserWithAuthorities();
            submissions = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exerciseId, user, examMode, correctionRound);
        }
        else {
            submissions = submissionService.getAllSubmissionsForExercise(exerciseId, submittedOnly, examMode);
        }

        submissions.forEach(submission -> {
            if (submission.getParticipation() != null) {
                submission.getParticipation().setExercise(null);
            }
            if (assessedByMe && !examMode) {
                submission.setResults(submission.getNonAthenaResults());
            }
        });
        return submissions;
    }
}
