package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.SubmissionService;

/**
 * Abstract class that allows reuse
 */
public abstract class AbstractSubmissionResource {

    protected final SubmissionRepository submissionRepository;

    protected final ResultService resultService;

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final SubmissionService submissionService;

    protected final StudentParticipationRepository studentParticipationRepository;

    public AbstractSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, AuthorizationCheckService authCheckService,
            UserRepository userRepository, ExerciseRepository exerciseRepository, SubmissionService submissionService,
            StudentParticipationRepository studentParticipationRepository) {
        this.submissionRepository = submissionRepository;
        this.resultService = resultService;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.submissionService = submissionService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Get all the submissions for an exercise. It is possible to filter, to receive only the ones that have already been submitted, or only the ones assessed by the tutor who is
     * doing the call. In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId the id of the exercise
     * @param submittedOnly if only submitted submissions should be returned
     * @param assessedByTutor if the submission was assessed by calling tutor
     * @return the ResponseEntity with status 200 (OK) and the list of submissions in body
     */
    protected ResponseEntity<List<Submission>> getAllSubmissions(Long exerciseId, boolean submittedOnly, boolean assessedByTutor, int correctionRound) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (assessedByTutor) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        }

        final boolean examMode = exercise.isExamExercise();
        List<Submission> submissions;
        if (assessedByTutor) {
            submissions = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exerciseId, user, examMode, correctionRound);
        }
        else {
            submissions = submissionService.getAllSubmissionsForExercise(exerciseId, submittedOnly, examMode);
        }

        // tutors should not see information about the student of a submission
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            submissions.forEach(submission -> submissionService.hideDetails(submission, user));
        }

        // remove unnecessary data from the REST response
        submissions.forEach(submission -> {
            if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
                submission.getParticipation().setExercise(null);
            }
        });

        return ResponseEntity.ok().body(submissions);
    }
}
