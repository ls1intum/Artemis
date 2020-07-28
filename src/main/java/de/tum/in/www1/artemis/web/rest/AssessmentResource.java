package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.*;

public abstract class AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(AssessmentResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final UserService userService;

    protected final ExerciseService exerciseService;

    protected final SubmissionService submissionService;

    protected final AssessmentService assessmentService;

    protected final ResultRepository resultRepository;

    protected final ExamService examService;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService, SubmissionService submissionService,
            AssessmentService assessmentService, ResultRepository resultRepository, ExamService examService) {
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.submissionService = submissionService;
        this.assessmentService = assessmentService;
        this.resultRepository = resultRepository;
        this.examService = examService;
    }

    abstract String getEntityName();

    /**
     * checks that the given user has at least tutor rights for the given exercise
     *
     * @param exercise the exercise for which the authorization should be checked
     * @throws AccessForbiddenException if current user is not at least teaching assistant in the given exercise
     * @throws BadRequestAlertException if no course is associated to the given exercise
     */
    void checkAuthorization(Exercise exercise, User user) throws AccessForbiddenException, BadRequestAlertException {
        validateExercise(exercise);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            log.debug("Insufficient permission for course: " + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
        }
    }

    void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this exercise or its exercise group and exam does not exist", getEntityName(), "courseNotFound");
        }
    }

    protected ResponseEntity<Void> cancelAssessment(long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        Submission submission = submissionService.findOneWithEagerResult(submissionId);
        if (submission.getResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseService.findOne(exerciseId);
        checkAuthorization(exercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!(isAtLeastInstructor || userService.getUser().getId().equals(submission.getResult().getAssessor().getId()))) {
            // tutors cannot cancel the assessment of other tutors (only instructors can)
            return forbidden();
        }
        assessmentService.cancelAssessmentOfSubmission(submission);
        return ResponseEntity.ok().build();
    }
}
