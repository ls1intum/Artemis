package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("/api")
public class TextExerciseResource {

    private final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

    private static final String ENTITY_NAME = "textExercise";

    private final TextAssessmentService textAssessmentService;
    private final TextExerciseService textExerciseService;
    private final TextExerciseRepository textExerciseRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;
    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository,
                                TextExerciseService textExerciseService,
                                TextAssessmentService textAssessmentService,
                                UserService userService,
                                AuthorizationCheckService authCheckService,
                                CourseService courseService,
                                ParticipationService participationService,
                                ResultRepository resultRepository,
                                ExampleSubmissionRepository exampleSubmissionRepository) {
        this.textAssessmentService = textAssessmentService;
        this.textExerciseService = textExerciseService;
        this.textExerciseRepository = textExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    /**
     * POST  /text-exercises : Create a new textExercise.
     *
     * @param textExercise the textExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textExercise, or with status 400 (Bad Request) if the textExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/text-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
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

        if (textExercise.getDueDate() == null && textExercise.getAssessmentDueDate() != null ) {
            throw new BadRequestAlertException("If you set an assessmentDueDate, then you need to add also a dueDate", ENTITY_NAME, "dueDate");
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this text exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return forbidden();
        }

        if (textExercise.getDueDate() != null && textExercise.getAssessmentDueDate() == null) {
            textExercise.setAssessmentDueDate(textExercise.getDueDate().plusWeeks(1));
        }

        TextExercise result = textExerciseRepository.save(textExercise);
        return ResponseEntity.created(new URI("/api/text-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /text-exercises : Updates an existing textExercise.
     *
     * @param textExercise the textExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textExercise,
     * or with status 400 (Bad Request) if the textExercise is not valid,
     * or with status 500 (Internal Server Error) if the textExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/text-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody TextExercise textExercise) throws URISyntaxException {
        log.debug("REST request to update TextExercise : {}", textExercise);
        if (textExercise.getId() == null) {
            return createTextExercise(textExercise);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this text exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return forbidden();
        }
        TextExercise result = textExerciseRepository.save(textExercise);

        // Avoid recursions
        if (textExercise.getExampleSubmissions().size() != 0) {
            result.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setExercise(null));
            result.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setTutorParticipation(null));
        }

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, textExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /courses/:courseId/exercises : get all the exercises.
     *
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
            //not required in the returned json body
            exercise.setParticipations(null);
            exercise.setCourse(null);
        }

        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET  /text-exercises/:id : get the "id" textExercise.
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
     * DELETE  /text-exercises/:id : delete the "id" textExercise.
     *
     * @param id the id of the textExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/text-exercises/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteTextExercise(@PathVariable Long id) {
        log.debug("REST request to delete TextExercise : {}", id);
        Optional<TextExercise> textExercise = textExerciseRepository.findById(id);
        if (textExercise.isPresent()) {
            Course course = textExercise.get().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isInstructorInCourse(course, user) &&
                !authCheckService.isAdmin()) {
                return forbidden();
            }
            textExerciseService.delete(id);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Returns the data needed for the text editor, which includes the participation, textSubmission with answer if existing
     * and the assessments if the submission was already submitted.
     *
     * @param participationId the participationId for which to find the data for the text editor
     * @return the ResponseEntity with json as body
     */
    @GetMapping("/text-editor/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Participation> getDataForTextEditor(@PathVariable Long participationId) {
        Participation participation = participationService.findOne(participationId);
        if (participation == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).body(null);
        }
        TextExercise textExercise;
        if (participation.getExercise() instanceof TextExercise) {
            textExercise = (TextExercise) participation.getExercise();
            if (textExercise == null) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textExercise", "exerciseEmpty", "The exercise belonging to the participation is null.")).body(null);
            }
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textExercise", "wrongExerciseType", "The exercise of the participation is not a modeling exercise.")).body(null);
        }

        // users can only see their own submission (to prevent cheating), TAs, instructors and admins can see all answers
        if (!authCheckService.isOwnerOfParticipation(participation) && !courseService.userHasAtLeastTAPermissions(textExercise.getCourse())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // if no results, check if there are really no results or the relation to results was not updated yet
        if (participation.getResults().size() <= 0) {
            List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
            participation.setResults(new HashSet<>(results));
        }

        Optional<TextSubmission> textSubmission = participation.findLatestTextSubmission();
        participation.setSubmissions(new HashSet<>());

        participation.getExercise().filterSensitiveInformation();

        if (textSubmission.isPresent()) {
            // set reference to participation to null, since we are already inside a participation
            textSubmission.get().setParticipation(null);

            Result result = textSubmission.get().getResult();
            if (textSubmission.get().isSubmitted() && result != null && result.getCompletionDate() != null) {
                List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
                result.setFeedbacks(assessments);
            }

            participation.addSubmissions(textSubmission.get());
        }


        return ResponseEntity.ok(participation);
    }
}
