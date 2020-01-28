package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing TextSubmission.
 */
@RestController
@RequestMapping("/api")
public class TextSubmissionResource {

    private static final String ENTITY_NAME = "textSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(TextSubmissionResource.class);

    private final TextSubmissionRepository textSubmissionRepository;

    private final ExerciseService exerciseService;

    private final TextExerciseService textExerciseService;

    private final CourseService courseService;

    private final AuthorizationCheckService authorizationCheckService;

    private final TextSubmissionService textSubmissionService;

    private final UserService userService;

    public TextSubmissionResource(TextSubmissionRepository textSubmissionRepository, ExerciseService exerciseService, TextExerciseService textExerciseService,
            CourseService courseService, AuthorizationCheckService authorizationCheckService, TextSubmissionService textSubmissionService, UserService userService) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.exerciseService = exerciseService;
        this.textExerciseService = textExerciseService;
        this.courseService = courseService;
        this.authorizationCheckService = authorizationCheckService;
        this.textSubmissionService = textSubmissionService;
        this.userService = userService;
    }

    /**
     * POST /exercises/{exerciseId}/text-submissions : Create a new textSubmission. This is called when a student saves his/her answer
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param textSubmission the textSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextSubmission> createTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to save TextSubmission : {}", textSubmission);
        if (textSubmission.getId() != null) {
            throw new BadRequestAlertException("A new textSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        return handleTextSubmission(exerciseId, principal, textSubmission);
    }

    /**
     * PUT /exercises/{exerciseId}/text-submissions : Updates an existing textSubmission. This function is called by the text editor for saving and submitting text submissions. The
     * submit specific handling occurs in the TextSubmissionService.save() function.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param textSubmission the textSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textSubmission, or with status 400 (Bad Request) if the textSubmission is not valid, or with status
     *         500 (Internal Server Error) if the textSubmission couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextSubmission> updateTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to update TextSubmission : {}", textSubmission);
        if (textSubmission.getId() == null) {
            return createTextSubmission(exerciseId, principal, textSubmission);
        }
        return handleTextSubmission(exerciseId, principal, textSubmission);
    }

    @NotNull
    private ResponseEntity<TextSubmission> handleTextSubmission(Long exerciseId, Principal principal, TextSubmission textSubmission) {
        final User user = userService.getUserWithGroupsAndAuthorities();
        final TextExercise textExercise = textExerciseService.findOne(exerciseId);
        final ResponseEntity<TextSubmission> responseFailure = this.checkExerciseValidity(textExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        textSubmission = textSubmissionService.handleTextSubmission(textSubmission, textExercise, principal);

        this.textSubmissionService.hideDetails(textSubmission, user);
        return ResponseEntity.ok(textSubmission);
    }

    /**
     * GET /text-submissions/:id : get the "id" textSubmission.
     *
     * @param id the id of the textSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/text-submissions/{id}")
    public ResponseEntity<TextSubmission> getTextSubmission(@PathVariable Long id) {
        log.debug("REST request to get TextSubmission : {}", id);
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(textSubmission);
    }

    private ResponseEntity<TextSubmission> checkExerciseValidity(TextExercise textExercise) {
        if (textExercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }

    /**
     * GET /text-submissions : get all the textSubmissions for an exercise. It is possible to filter, to receive only the one that have been already submitted, or only the one
     * assessed by the tutor who is doing the call
     *
     * @param exerciseId exerciseID  for which all submissions should be returned
     * @param submittedOnly mark if only submitted Submissions should be returned
     * @param assessedByTutor mark if only assessed Submissions should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: separate this into 2 calls, one for instructors (with all submissions) and one for tutors (only the submissions for the requesting tutor)
    public ResponseEntity<List<TextSubmission>> getAllTextSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all TextSubmissions");
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = textExerciseService.findOne(exerciseId);
        if (assessedByTutor) {
            if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                throw new AccessForbiddenException("You are not allowed to access this resource");
            }
        }
        else if (!authorizationCheckService.isAtLeastInstructorForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        final List<TextSubmission> textSubmissions;
        if (assessedByTutor) {
            textSubmissions = textSubmissionService.getAllTextSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            textSubmissions = textSubmissionService.getTextSubmissionsByExerciseId(exerciseId, submittedOnly);
        }

        // tutors should not see information about the student of a submission
        if (!authorizationCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            textSubmissions.forEach(submission -> textSubmissionService.hideDetails(submission, user));
        }

        // remove unnecessary data from the REST response
        textSubmissions.forEach(submission -> {
            if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
                submission.getParticipation().setExercise(null);
            }
        });

        return ResponseEntity.ok().body(textSubmissions);
    }

    /**
     * GET /text-submission-without-assessment : get one textSubmission without assessment.
     *
     * @param exerciseId exerciseID  for which a submission should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/text-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<TextSubmission> getTextSubmissionWithoutAssessment(@PathVariable Long exerciseId) {
        log.debug("REST request to get a text submission without assessment");
        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);

        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        if (!(exercise instanceof TextExercise)) {
            return badRequest();
        }

        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())) {
            return notFound();
        }

        // Check if the limit of simultaneously locked submissions has been reached
        textSubmissionService.checkSubmissionLockLimit(exercise.getCourse().getId());

        Optional<TextSubmission> textSubmissionWithoutAssessment = this.textSubmissionService.getTextSubmissionWithoutManualResult((TextExercise) exercise);

        // tutors should not see information about the student of a submission
        if (textSubmissionWithoutAssessment.isPresent() && textSubmissionWithoutAssessment.get().getParticipation() != null
                && textSubmissionWithoutAssessment.get().getParticipation() instanceof StudentParticipation) {
            ((StudentParticipation) textSubmissionWithoutAssessment.get().getParticipation()).filterSensitiveInformation();
        }

        return ResponseUtil.wrapOrNotFound(textSubmissionWithoutAssessment);
    }
}
