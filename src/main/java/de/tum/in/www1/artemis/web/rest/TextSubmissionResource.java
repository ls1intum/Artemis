package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing TextSubmission.
 */
@RestController
@RequestMapping("/api")
public class TextSubmissionResource {

    private static final String ENTITY_NAME = "textSubmission";
    private final Logger log = LoggerFactory.getLogger(TextSubmissionResource.class);
    private final TextSubmissionRepository textSubmissionRepository;
    private final ResultRepository resultRepository;
    private final ExerciseService exerciseService;
    private final TextExerciseService textExerciseService;
    private final CourseService courseService;
    private final ParticipationService participationService;
    private final TextSubmissionService textSubmissionService;
    private final UserService userService;
    private final AuthorizationCheckService authCheckService;

    public TextSubmissionResource(TextSubmissionRepository textSubmissionRepository,
                                  ExerciseService exerciseService,
                                  TextExerciseService textExerciseService,
                                  CourseService courseService,
                                  ParticipationService participationService,
                                  TextSubmissionService textSubmissionService,
                                  UserService userService,
                                  ResultRepository resultRepository,
                                  AuthorizationCheckService authCheckService) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.exerciseService = exerciseService;
        this.textExerciseService = textExerciseService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.textSubmissionService = textSubmissionService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
    }

    /**
     * POST  /exercises/{exerciseId}/text-submissions : Create a new textSubmission.
     * This is called when a student saves his/her answer
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param textSubmission the textSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ResponseEntity<TextSubmission> createTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to save TextSubmission : {}", textSubmission);
        if (textSubmission.getId() != null) {
            throw new BadRequestAlertException("A new textSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        return handleTextSubmission(exerciseId, principal, textSubmission);
    }

    /**
     * PUT  /exercises/{exerciseId}/text-submissions : Updates an existing textSubmission.
     * This function is called by the text editor for saving and submitting text submissions.
     * The submit specific handling occurs in the TextSubmissionService.save() function.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param textSubmission the textSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textSubmission,
     * or with status 400 (Bad Request) if the textSubmission is not valid,
     * or with status 500 (Internal Server Error) if the textSubmission couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<TextSubmission> updateTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to update TextSubmission : {}", textSubmission);
        if (textSubmission.getId() == null) {
            return createTextSubmission(exerciseId, principal, textSubmission);
        }

        return handleTextSubmission(exerciseId, principal, textSubmission);
    }

    @NotNull
    private ResponseEntity<TextSubmission> handleTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        ResponseEntity<TextSubmission> responseFailure = this.checkExerciseValidity(textExercise);
        if (responseFailure != null) return responseFailure;

        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());

        // update and save submission
        textSubmission = textSubmissionService.save(textSubmission, textExercise, participation);
        hideDetails(textSubmission);
        return ResponseEntity.ok(textSubmission);
    }

    //TODO: move this code to textSubmission which invokes the general part with super.hideDetails()
    private void hideDetails(@RequestBody TextSubmission textSubmission) {
        //do not send old submissions or old results to the client
        if (textSubmission.getParticipation() != null) {
            textSubmission.getParticipation().setSubmissions(null);
            textSubmission.getParticipation().setResults(null);

            if (textSubmission.getParticipation().getExercise() != null && textSubmission.getParticipation().getExercise() instanceof TextExercise) {
                //make sure the solution is not sent to the client
                TextExercise textExerciseForClient = (TextExercise) textSubmission.getParticipation().getExercise();
                textExerciseForClient.setSampleSolution(null);
            }
        }
    }


    /**
     * GET  /text-submissions/:id : get the "id" textSubmission.
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
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this text exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }

    /**
     * GET  /text-submissions : get all the textSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TextSubmission>> getAllTextSubmissions(@PathVariable Long exerciseId,
                                                                      @RequestParam(defaultValue = "false") boolean submittedOnly) {
        log.debug("REST request to get all TextSubmissions");
        Exercise exercise = exerciseService.findOneLoadParticipations(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!hasAtLeastTAPermissions(exercise, user)) return forbidden();

        return ResponseEntity.ok().body(

            // Participations for Exercise
            participationService.findByExerciseIdWithEagerSubmissions(exerciseId)
                .stream()
                .peek(participation -> participation.getExercise().setParticipations(null))

                // Map to Latest Submission
                .map(Participation::findLatestTextSubmission)
                .filter(Objects::nonNull)

                // if submittedOnly, need to check if submitted, else just continue (!submittedOnly == true)
                .filter(submission -> !submittedOnly || submission.isSubmitted())

                // Load Result for Submission
                .peek(textSubmission -> {
                    Hibernate.initialize(textSubmission.getResult()); // eagerly load the association
                    if (textSubmission.getResult() != null) {
                        Hibernate.initialize(textSubmission.getResult().getAssessor());
                        textSubmission.getResult().getAssessor().setGroups(null);
                    }
                })

                // Convert Stream to List to Match Return Type
                .collect(Collectors.toList())
        );
    }

    /**
     * GET  /text-submissions-assessed-by-tutor : get all the textSubmissions assessed by the current tutor.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/text-submissions-assessed-by-tutor")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TextSubmission>> getAllTextSubmissionsByTutor(@PathVariable Long exerciseId) {
        log.debug("REST request to get all TextSubmissions filtered by assessed by tutor");
        Exercise exercise = exerciseService.findOneLoadParticipations(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!hasAtLeastTAPermissions(exercise, user)) return forbidden();

        return ResponseEntity.ok().body(

            // Participations for Exercise
            participationService.findByExerciseIdWithEagerSubmissions(exerciseId)
                .stream()
                .peek(participation -> {
                    participation.getExercise().setParticipations(null);
                })

                // Map to Latest Submission
                .map(Participation::findLatestTextSubmission)
                .filter(Objects::nonNull)

                // It needs to be submitted to have an assessment
                .filter(Submission::isSubmitted)

                .filter(textSubmission -> {
                    Result result = resultRepository.findDistinctBySubmissionId(textSubmission.getId()).orElse(null);
                    if (result == null) {
                        return false;
                    }

                    return result.getAssessor() == user;
                })

                // Load Result for Submission
                .peek(textSubmission -> {
                    Hibernate.initialize(textSubmission.getResult()); // eagerly load the association
                    if (textSubmission.getResult() != null) {
                        Hibernate.initialize(textSubmission.getResult().getAssessor());
                        textSubmission.getResult().getAssessor().setGroups(null);
                    }
                })

                // Convert Stream to List to Match Return Type
                .collect(Collectors.toList())
        );
    }

    /**
     * GET  /text-submission-without-assessment : get one textSubmission without assessment.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/text-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<TextSubmission> getTextSubmissionWithoutAssessment(@PathVariable Long exerciseId) {
        log.debug("REST request to get a text submission without assessment");
        Exercise exercise = exerciseService.findOneLoadParticipations(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!hasAtLeastTAPermissions(exercise, user)) return forbidden();

        Optional<TextSubmission> textSubmissionWithoutAssessment = this.textSubmissionService.textSubmissionWithoutResult(exerciseId);

        return ResponseUtil.wrapOrNotFound(textSubmissionWithoutAssessment);
    }

    private boolean hasAtLeastTAPermissions(Exercise exercise, User user) {
        Course course = exercise.getCourse();

        return authCheckService.isTeachingAssistantInCourse(course, user) ||
            authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isAdmin();
    }
}
