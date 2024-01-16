package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.math.MathExercise;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.dto.MathOCRRequestDTO;
import de.tum.in.www1.artemis.service.dto.MathOCRResponseDTO;
import de.tum.in.www1.artemis.service.dto.MathOCRTokenResponseDTO;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationScheduleService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing math exercises.
 */
@RestController
@RequestMapping("api/")
public class MathExerciseResource {

    private final Logger log = LoggerFactory.getLogger(MathExerciseResource.class);

    private static final String ENTITY_NAME = "mathExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MathExerciseService mathExerciseService;

    private final MathOCRService mathOCRService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final MathExerciseRepository mathExerciseRepository;

    private final MathExerciseImportService mathExerciseImportService;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationRepository participationRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    public MathExerciseResource(MathExerciseRepository mathExerciseRepository, MathExerciseService mathExerciseService, ExerciseDeletionService exerciseDeletionService,
            UserRepository userRepository, AuthorizationCheckService authCheckService, CourseService courseService, ParticipationRepository participationRepository,
            MathExerciseImportService mathExerciseImportService, ExampleSubmissionRepository exampleSubmissionRepository, ExerciseService exerciseService,
            GradingCriterionRepository gradingCriterionRepository, GroupNotificationScheduleService groupNotificationScheduleService, CourseRepository courseRepository,
            ChannelService channelService, ChannelRepository channelRepository, MathOCRService mathOCRService) {
        this.exerciseDeletionService = exerciseDeletionService;
        this.mathExerciseService = mathExerciseService;
        this.mathExerciseRepository = mathExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationRepository = participationRepository;
        this.mathExerciseImportService = mathExerciseImportService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exerciseService = exerciseService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.courseRepository = courseRepository;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.mathOCRService = mathOCRService;
    }

    /**
     * POST /math-exercises : Create a new math exercise.
     *
     * @param mathExercise the math exercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new math exercise, or
     *         with status 400 (Bad Request) if the math exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExercise> createMathExercise(@RequestBody MathExercise mathExercise) throws URISyntaxException {
        log.debug("REST request to save MathExercise : {}", mathExercise);
        if (mathExercise.getId() != null) {
            throw new BadRequestAlertException("A new math exercise cannot already have an ID", ENTITY_NAME, "idExists");
        }

        if (mathExercise.getTitle() == null) {
            throw new BadRequestAlertException("A new math exercise needs a title", ENTITY_NAME, "missingtitle");
        }
        // validates general settings: points, dates
        mathExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        mathExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(mathExercise);
        // Check that the user is authorized to create the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        MathExercise result = mathExerciseRepository.save(mathExercise);

        channelService.createExerciseChannel(result, Optional.ofNullable(mathExercise.getChannelName()));
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(mathExercise);

        return ResponseEntity.created(new URI("/api/math-exercises/" + result.getId())).body(result);
    }

    /**
     * PUT /math-exercises : Updates an existing math exercise.
     *
     * @param mathExercicse    the math exercise to update
     * @param notificationText about the math exercise update that should be displayed for the
     *                             student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated math exercise, or
     *         with status 400 (Bad Request) if the math exercise is not valid, or with status 500 (Internal
     *         Server Error) if the math exercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExercise> updateMathExercise(@RequestBody MathExercise mathExercicse,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update MathExercisce : {}", mathExercicse);
        if (mathExercicse.getId() == null) {
            return createMathExercise(mathExercicse);
        }
        // validates general settings: points, dates
        mathExercicse.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        mathExercicse.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check
        final MathExercise mathExerciseBeforeUpdate = mathExerciseRepository.findByIdElseThrow(mathExercicse.getId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, mathExerciseBeforeUpdate, user);

        // Forbid changing the course the exercise belongs to.
        if (!Objects.equals(mathExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId(), mathExercicse.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(mathExercicse, mathExerciseBeforeUpdate, ENTITY_NAME);

        channelService.updateExerciseChannel(mathExerciseBeforeUpdate, mathExercicse);

        MathExercise updatedMathExercise = mathExerciseRepository.save(mathExercicse);
        exerciseService.logUpdate(updatedMathExercise, updatedMathExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(mathExerciseBeforeUpdate, updatedMathExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedMathExercise, mathExerciseBeforeUpdate.getDueDate());
        exerciseService.checkExampleSubmissions(updatedMathExercise);
        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(mathExerciseBeforeUpdate, updatedMathExercise, notificationText);
        return ResponseEntity.ok(updatedMathExercise);
    }

    /**
     * GET /courses/:courseId/math-exercises : get all the math exercises.
     *
     * @param courseId id of the course of which all the math exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of math exercises in body
     */
    @GetMapping("courses/{courseId}/math-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MathExercise>> getMathExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<MathExercise> exercises = mathExerciseRepository.findByCourseIdWithCategories(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
            List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
            exercise.setGradingCriteria(gradingCriteria);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /math-exercises/:id : get the math exercise by id.
     *
     * @param exerciseId the id of the math exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the math exercisce, or with status 404 (Not Found)
     */
    @GetMapping("math-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<MathExercise> getMathExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get MathExercise : {}", exerciseId);
        var mathExercise = mathExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("MathExercise", exerciseId));

        if (mathExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, mathExercise, null);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, mathExercise, null);
        }

        if (mathExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(mathExercise.getId());
            if (channel != null) {
                mathExercise.setChannelName(channel.getName());
            }
        }

        Set<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllWithResultByExerciseId(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        mathExercise.setGradingCriteria(gradingCriteria);
        mathExercise.setExampleSubmissions(exampleSubmissions);

        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, mathExercise);
        return ResponseEntity.ok().body(mathExercise);
    }

    /**
     * DELETE /math-exercises/:exerciseId : delete the math exercise by id.
     *
     * @param exerciseId the id of the math exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("math-exercises/{exerciseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteMathExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete MathExercise : {}", exerciseId);
        var mathExercise = mathExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, mathExercise, user);
        // NOTE: we use the exerciseDeletionService here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(mathExercise, mathExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, mathExercise.getTitle())).build();
    }

    /**
     * Search for all math exercises by id, title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<MathExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search, @RequestParam(defaultValue = "true") boolean isCourseFilter,
            @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(mathExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    /**
     * POST /math-exercises/import: Imports an existing math exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates. Referenced
     * entities will get cloned and assigned a new id.
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the
     *                             imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     *         or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("math-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExercise> importMathExercise(@PathVariable long sourceExerciseId, @RequestBody MathExercise importedExercise) throws URISyntaxException {
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity("Math Exercise");
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalExercise = mathExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();

        final var newExercise = mathExerciseImportService.importMathExercise(originalExercise, importedExercise);
        mathExerciseRepository.save(newExercise);
        return ResponseEntity.created(new URI("/api/math-exercises/" + newExercise.getId())).body(newExercise);
    }

    /**
     * PUT /math-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing math exercise.
     *
     * @param exerciseId                                  of the exercise
     * @param mathExercise                                the math exercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated math exercise, or
     *         with status 400 (Bad Request) if the math exercise is not valid, or with status 409 (Conflict)
     *         if given exerciseId is not same as in the object of the request body, or with status 500
     *         (Internal Server Error) if the math exercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("math-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExercise> reEvaluateAndUpdateMathExercise(@PathVariable long exerciseId, @RequestBody MathExercise mathExercise,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) throws URISyntaxException {
        log.debug("REST request to re-evaluate MathExercise : {}", mathExercise);

        // check that the exercise exists for given id
        mathExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);

        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, mathExercise);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(mathExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(mathExercise, deleteFeedbackAfterGradingInstructionUpdate);

        return updateMathExercise(mathExercise, null);
    }

    /**
     * GET /math-exercises/{exerciseId}/editor/ocr/token : Get a short-lived client token for OCR
     * for the exercise with the given id. If the OCR service is not enabled, or OCR is not available
     * for the given exercise, a bad request exception is thrown.
     *
     * @param exerciseId id of the exercise for which to get the token
     * @return the ResponseEntity with status 200 (OK) and the token in the body, or with status 400
     *         (Bad Request) if OCR is not enabled for the exercise.
     */
    @PostMapping("math-exercises/{exerciseId}/editor/ocr/token")
    @EnforceAtLeastStudent
    public ResponseEntity<MathOCRTokenResponseDTO> getOCRClientToken(@PathVariable long exerciseId) {
        log.debug("REST request to get OCR client token");

        // check that the exercise exists for given id
        MathExercise mathExercise = mathExerciseRepository.findByIdElseThrow(exerciseId);

        this.checkOCREnabled(mathExercise);

        try {
            return ResponseEntity.ok(mathOCRService.getClientToken());
        }
        catch (Exception e) {
            log.error("Error while performing OCR", e);
            throw new BadRequestException("OCR could not be performed");
        }
    }

    /**
     * POST /math-exercises/{exerciseId}/editor/ocr/process : Process an OCR request for the exercise
     * with the given id. If the OCR service is not enabled, or OCR is not available for the given
     * exercise, a bad request exception is thrown. If the OCR request is invalid, a bad request
     * exception is thrown.
     *
     * <p>
     * The request body must contain either an image or strokes, but not both. Each of the fields
     * follows the format of the <a href="https://docs.mathpix.com/#introduction">Mathpix API</a>.
     * <ul>
     * <li>If an image is provided, it must be a base64 encoded string of a png or jpeg image.</li>
     * <li>If strokes are provided, they must be a list of strokes, separated by x and y coordinates.</li>
     * </ul>
     * </p>
     *
     * @param exerciseId id of the exercise for which to process the OCR request
     * @param ocrRequest the OCR request
     * @return the ResponseEntity with status 200 (OK) and the OCR response in the body, or with
     *         status 400 (Bad Request) if OCR is not enabled for the exercise, or if the OCR request is
     *         invalid.
     */
    @PostMapping("math-exercises/{exerciseId}/editor/ocr/process")
    @EnforceAtLeastStudent
    public ResponseEntity<MathOCRResponseDTO> processOCRFromImageOrStroke(@PathVariable long exerciseId, @RequestBody MathOCRRequestDTO ocrRequest) {
        log.debug("REST request to get OCR expression from image or stroke : {}", ocrRequest);

        // check that the exercise exists for given id
        MathExercise mathExercise = mathExerciseRepository.findByIdElseThrow(exerciseId);

        this.checkOCREnabled(mathExercise);

        if (ocrRequest.image() == null && ocrRequest.strokes() == null) {
            throw new BadRequestException("Either an image or strokes must be provided");
        }

        try {
            return ResponseEntity.ok(mathOCRService.getExpressionFromImageOrStroke(ocrRequest));
        }
        catch (Exception e) {
            log.error("Error while processing OCR", e);
            throw new BadRequestException("OCR could not be performed");
        }
    }

    /**
     * Checks if OCR is enabled for the given exercise. If not, a bad request exception is thrown.
     *
     * @param exercise the exercise to check
     * @throws BadRequestException if OCR is not enabled for the exercise
     */
    private void checkOCREnabled(MathExercise exercise) throws BadRequestException {
        // TODO: OCR is currently enabled for all exercises, enable this check
        // once we have a way to enable/disable OCR for exercises
        // if (exercise.getInputConfiguration() != null && (exercise.getInputConfiguration() instanceof MathExerciseExpressionInputConfiguration expressionInputConfiguration)
        // && expressionInputConfiguration.ocrEnabled()) {
        // return;
        // }
        // throw new BadRequestException("OCR is not enabled for this exercise");
    }
}
