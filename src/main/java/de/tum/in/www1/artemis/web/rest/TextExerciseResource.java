package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.scheduled.TextClusteringScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/** REST controller for managing TextExercise. */
@RestController
@RequestMapping("/api")
public class TextExerciseResource {

    private final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

    private static final String ENTITY_NAME = "textExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TextAssessmentService textAssessmentService;

    private final TextExerciseService textExerciseService;

    private final ExerciseService exerciseService;

    private final TextExerciseRepository textExerciseRepository;

    private final UserService userService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final GroupNotificationService groupNotificationService;

    private final Optional<TextClusteringScheduleService> textClusteringScheduleService;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository, TextExerciseService textExerciseService, TextAssessmentService textAssessmentService,
            UserService userService, AuthorizationCheckService authCheckService, CourseService courseService, ParticipationService participationService,
            ResultRepository resultRepository, GroupNotificationService groupNotificationService, ExampleSubmissionRepository exampleSubmissionRepository,
            Optional<TextClusteringScheduleService> textClusteringScheduleService, ExerciseService exerciseService) {
        this.textAssessmentService = textAssessmentService;
        this.textExerciseService = textExerciseService;
        this.textExerciseRepository = textExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.groupNotificationService = groupNotificationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.textClusteringScheduleService = textClusteringScheduleService;
        this.exerciseService = exerciseService;
    }

    /**
     * POST /text-exercises : Create a new textExercise.
     *
     * @param textExercise the textExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textExercise, or with status 400 (Bad Request) if the textExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/text-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextExercise> createTextExercise(@RequestBody TextExercise textExercise) throws URISyntaxException {
        log.debug("REST request to save TextExercise : {}", textExercise);
        if (textExercise.getId() != null) {
            throw new BadRequestAlertException("A new textExercise cannot already have an ID", ENTITY_NAME, "idexists");
        }

        if (textExercise.getTitle() == null) {
            throw new BadRequestAlertException("A new textExercise needs a title", ENTITY_NAME, "missingtitle");
        }

        if (textExercise.getMaxScore() == null) {
            throw new BadRequestAlertException("A new textExercise needs a max score", ENTITY_NAME, "missingmaxscore");
        }

        if (textExercise.getDueDate() == null && textExercise.getAssessmentDueDate() != null) {
            throw new BadRequestAlertException("If you set an assessmentDueDate, then you need to add also a dueDate", ENTITY_NAME, "dueDate");
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }
        if (textExercise.isAutomaticAssessmentEnabled() && !authCheckService.isAdmin()) {
            return forbidden();
        }

        TextExercise result = textExerciseRepository.save(textExercise);
        textClusteringScheduleService.ifPresent(service -> service.scheduleExerciseForClusteringIfRequired(result));
        groupNotificationService.notifyTutorGroupAboutExerciseCreated(textExercise);
        return ResponseEntity.created(new URI("/api/text-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /text-exercises : Updates an existing textExercise.
     *
     * @param textExercise the textExercise to update
     * @param notificationText about the text exercise update that should be displayed for the student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated textExercise, or with status 400 (Bad Request) if the textExercise is not valid, or with status 500
     *         (Internal Server Error) if the textExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/text-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody TextExercise textExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update TextExercise : {}", textExercise);
        if (textExercise.getId() == null) {
            return createTextExercise(textExercise);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }
        TextExercise textExerciseBeforeUpdate = textExerciseService.findOne(textExercise.getId());
        if (textExerciseBeforeUpdate.isAutomaticAssessmentEnabled() != textExercise.isAutomaticAssessmentEnabled() && !authCheckService.isAdmin()) {
            return forbidden();
        }
        TextExercise result = textExerciseRepository.save(textExercise);
        textClusteringScheduleService.ifPresent(service -> service.scheduleExerciseForClusteringIfRequired(result));

        // Avoid recursions
        if (textExercise.getExampleSubmissions().size() != 0) {
            result.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setExercise(null));
            result.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setTutorParticipations(null));
        }

        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(textExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, textExercise.getId().toString())).body(result);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId id of the course of which all the exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of textExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/text-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<TextExercise>> getTextExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return forbidden();
        }
        List<TextExercise> exercises = textExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }

        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /text-exercises/:id : get the "id" textExercise.
     *
     * @param exerciseId the id of the textExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textExercise, or with status 404 (Not Found)
     */
    @GetMapping("/text-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextExercise> getTextExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get TextExercise : {}", exerciseId);
        Optional<TextExercise> optionalTextExercise = textExerciseRepository.findById(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(optionalTextExercise)) {
            return forbidden();
        }

        Set<ExampleSubmission> exampleSubmissions = new HashSet<>(this.exampleSubmissionRepository.findAllByExerciseId(exerciseId));
        optionalTextExercise.ifPresent(textExercise -> textExercise.setExampleSubmissions(exampleSubmissions));

        return ResponseUtil.wrapOrNotFound(optionalTextExercise);
    }

    /**
     * DELETE /text-exercises/:id : delete the "id" textExercise.
     *
     * @param exerciseId the id of the textExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/text-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteTextExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete TextExercise : {}", exerciseId);
        Optional<TextExercise> textExercise = textExerciseRepository.findById(exerciseId);
        if (textExercise.isEmpty()) {
            return notFound();
        }
        Course course = textExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        textClusteringScheduleService.ifPresent(service -> service.cancelScheduledClustering(textExercise.get()));
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(textExercise.get(), course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, textExercise.get().getTitle())).build();

    }

    /**
     * Returns the data needed for the text editor, which includes the participation, textSubmission with answer if existing and the assessments if the submission was already
     * submitted.
     *
     * @param participationId the participationId for which to find the data for the text editor
     * @return the ResponseEntity with the participation as body
     */
    @GetMapping("/text-editor/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentParticipation> getDataForTextEditor(@PathVariable Long participationId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = participationService.findOneStudentParticipationWithEagerSubmissionsResultsExerciseAndCourse(participationId);
        if (participation == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).body(null);
        }
        TextExercise textExercise;
        if (participation.getExercise() instanceof TextExercise) {
            textExercise = (TextExercise) participation.getExercise();
            if (textExercise == null) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "textExercise", "exerciseEmpty", "The exercise belonging to the participation is null."))
                        .body(null);
            }
        }
        else {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "textExercise", "wrongExerciseType", "The exercise of the participation is not a modeling exercise."))
                    .body(null);
        }

        // users can only see their own submission (to prevent cheating), TAs, instructors and admins can see all answers
        if (!authCheckService.isOwnerOfParticipation(participation, user) && !authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // if no results, check if there are really no results or the relation to results was not
        // updated yet
        if (participation.getResults().size() <= 0) {
            List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
            participation.setResults(new HashSet<>(results));
        }

        Optional<Submission> optionalSubmission = participation.findLatestSubmission();
        participation.setSubmissions(new HashSet<>());

        participation.getExercise().filterSensitiveInformation();

        if (optionalSubmission.isPresent()) {
            TextSubmission textSubmission = (TextSubmission) optionalSubmission.get();

            // set reference to participation to null, since we are already inside a participation
            textSubmission.setParticipation(null);

            Result result = textSubmission.getResult();
            if (textSubmission.isSubmitted() && result != null && result.getCompletionDate() != null) {
                List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
                result.setFeedbacks(assessments);
            }

            if (result != null && !authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
                result.setAssessor(null);
            }

            participation.addSubmissions(textSubmission);
        }

        if (!authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            participation.setStudent(null);
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * POST /text-exercises/{exerciseId}/trigger-automatic-assessment: trigger automatic assessment (clustering task) for given exercise id
     *
     * @param exerciseId id of the exercised that for which the automatic assessment should be triggered
     * @return the ResponseEntity with status 200 (OK) or with status 400 (Bad Request)
     */
    @PostMapping("/text-exercises/{exerciseId}/trigger-automatic-assessment")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> triggerAutomaticAssessment(@PathVariable Long exerciseId) {
        if (textClusteringScheduleService.isPresent()) {
            TextExercise textExercise = textExerciseService.findOne(exerciseId);
            textClusteringScheduleService.get().scheduleExerciseForInstantClustering(textExercise);
            return ResponseEntity.ok().build();
        }
        else {
            return ResponseEntity.badRequest().build();
        }
    }
}
