package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jplag.ExitException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.plagiarism.TextPlagiarismDetectionService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("/api")
public class TextExerciseResource {

    private final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

    private static final String ENTITY_NAME = "textExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TextAssessmentService textAssessmentService;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseService textExerciseService;

    private final ExerciseService exerciseService;

    private final TextExerciseRepository textExerciseRepository;

    private final TextExerciseImportService textExerciseImportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final GroupNotificationService groupNotificationService;

    private final GradingCriterionService gradingCriterionService;

    private final ExerciseGroupService exerciseGroupService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final TextPlagiarismDetectionService textPlagiarismDetectionService;

    private final CourseRepository courseRepository;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository, TextExerciseService textExerciseService, TextAssessmentService textAssessmentService,
            UserRepository userRepository, AuthorizationCheckService authCheckService, CourseService courseService, StudentParticipationRepository studentParticipationRepository,
            ResultRepository resultRepository, GroupNotificationService groupNotificationService, TextExerciseImportService textExerciseImportService,
            TextSubmissionExportService textSubmissionExportService, ExampleSubmissionRepository exampleSubmissionRepository, ExerciseService exerciseService,
            GradingCriterionService gradingCriterionService, TextBlockRepository textBlockRepository, ExerciseGroupService exerciseGroupService,
            InstanceMessageSendService instanceMessageSendService, TextPlagiarismDetectionService textPlagiarismDetectionService, CourseRepository courseRepository) {
        this.textAssessmentService = textAssessmentService;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseService = textExerciseService;
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.groupNotificationService = groupNotificationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exerciseService = exerciseService;
        this.gradingCriterionService = gradingCriterionService;
        this.exerciseGroupService = exerciseGroupService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.textPlagiarismDetectionService = textPlagiarismDetectionService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /text-exercises : Create a new textExercise.
     *
     * @param textExercise the textExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textExercise, or
     * with status 400 (Bad Request) if the textExercise has already an ID
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

        // Validate score settings
        Optional<ResponseEntity<TextExercise>> optionalScoreSettingsError = exerciseService.validateScoreSettings(textExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        if (textExercise.getDueDate() == null && textExercise.getAssessmentDueDate() != null) {
            throw new BadRequestAlertException("If you set an assessmentDueDate, then you need to add also a dueDate", ENTITY_NAME, "dueDate");
        }

        // Valid exercises have set either a course or an exerciseGroup
        textExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);

        // Check that the user is authorized to create the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        TextExercise result = textExerciseRepository.save(textExercise);
        instanceMessageSendService.sendTextExerciseSchedule(result.getId());

        // Only notify tutors when the exercise is created for a course
        if (textExercise.isCourseExercise()) {
            groupNotificationService.notifyTutorGroupAboutExerciseCreated(textExercise);
        }
        return ResponseEntity.created(new URI("/api/text-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /text-exercises : Updates an existing textExercise.
     *
     * @param textExercise     the textExercise to update
     * @param notificationText about the text exercise update that should be displayed for the
     *                         student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated textExercise, or
     * with status 400 (Bad Request) if the textExercise is not valid, or with status 500 (Internal
     * Server Error) if the textExercise couldn't be updated
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

        // Validate score settings
        Optional<ResponseEntity<TextExercise>> optionalScoreSettingsError = exerciseService.validateScoreSettings(textExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        // Valid exercises have set either a course or an exerciseGroup
        textExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        TextExercise textExerciseBeforeUpdate = textExerciseRepository.findByIdElseThrow(textExercise.getId());

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(textExercise, textExerciseBeforeUpdate, ENTITY_NAME);

        TextExercise result = textExerciseRepository.save(textExercise);
        instanceMessageSendService.sendTextExerciseSchedule(result.getId());

        // Avoid recursions
        if (textExercise.getExampleSubmissions().size() != 0) {
            Set<ExampleSubmission> exampleSubmissionsWithResults = exampleSubmissionRepository.findAllWithEagerResultByExerciseId(textExercise.getId());
            result.setExampleSubmissions(exampleSubmissionsWithResults);
            result.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setExercise(null));
            result.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setTutorParticipations(null));
        }

        // Only notify students about changes if a regular exercise was updated
        if (notificationText != null && textExercise.isCourseExercise()) {
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
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return forbidden();
        }
        List<TextExercise> exercises = textExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
            List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
            exercise.setGradingCriteria(gradingCriteria);

        }

        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /text-exercises/:id : get the "id" textExercise.
     *
     * @param exerciseId the id of the textExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textExercise, or with
     * status 404 (Not Found)
     */
    @GetMapping("/text-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextExercise> getTextExercise(@PathVariable Long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get TextExercise : {}", exerciseId);
        Optional<TextExercise> optionalTextExercise = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesById(exerciseId);

        if (optionalTextExercise.isEmpty()) {
            return notFound();
        }
        TextExercise textExercise = optionalTextExercise.get();

        // If the exercise belongs to an exam, only instructors and admins are allowed to access it
        if (textExercise.isExamExercise()) {
            // Get the course over the exercise group
            ExerciseGroup exerciseGroup = exerciseGroupService.findOneWithExam(textExercise.getExerciseGroup().getId());
            Course course = exerciseGroup.getExam().getCourse();

            if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
                return forbidden();
            }
            // Set the exerciseGroup, exam and course so that the client can work with those ids
            textExercise.setExerciseGroup(exerciseGroup);
        }
        else if (!authCheckService.isAtLeastTeachingAssistantForExercise(optionalTextExercise)) {
            return forbidden();
        }

        Set<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllWithEagerResultByExerciseId(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        textExercise.setGradingCriteria(gradingCriteria);
        textExercise.setExampleSubmissions(exampleSubmissions);

        return ResponseEntity.ok().body(textExercise);
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
        Optional<TextExercise> optionalTextExercise = textExerciseRepository.findById(exerciseId);
        if (optionalTextExercise.isEmpty()) {
            return notFound();
        }
        TextExercise textExercise = optionalTextExercise.get();

        // If the exercise belongs to an exam, the course must be retrieved over the exerciseGroup
        Course course;
        if (textExercise.isExamExercise()) {
            course = exerciseGroupService.retrieveCourseOverExerciseGroup(textExercise.getExerciseGroup().getId());
        }
        else {
            course = textExercise.getCourseViaExerciseGroupOrCourseMember();
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        instanceMessageSendService.sendTextExerciseScheduleCancel(textExercise.getId());
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(textExercise, course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, textExercise.getTitle())).build();
    }

    /**
     * Returns the data needed for the text editor, which includes the participation, textSubmission
     * with answer if existing and the assessments if the submission was already submitted.
     *
     * @param participationId the participationId for which to find the data for the text editor
     * @return the ResponseEntity with the participation as body
     */
    // TODO: fix the URL scheme
    @GetMapping("/text-editor/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentParticipation> getDataForTextEditor(@PathVariable Long participationId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = studentParticipationRepository.findByIdWithSubmissionsResultsFeedbackElseThrow(participationId);
        if (!(participation.getExercise() instanceof TextExercise)) {
            throw new BadRequestAlertException("The exercise of the participation is not a text exercise.", ENTITY_NAME, "wrongExerciseType");
        }
        TextExercise textExercise = (TextExercise) participation.getExercise();

        // users can only see their own submission (to prevent cheating), TAs, instructors and admins can see all answers
        if (!authCheckService.isOwnerOfParticipation(participation, user) && !authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Exam exercises cannot be seen by students between the endDate and the publishResultDate
        if (!authCheckService.isAllowedToGetExamResult(textExercise, user)) {
            return forbidden();
        }

        // if no results, check if there are really no results or the relation to results was not updated yet
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

            Result result = textSubmission.getLatestResult();
            if (result != null) {
                // Load TextBlocks for the Submission. They are needed to display the Feedback in the client.
                final var textBlocks = textBlockRepository.findAllBySubmissionId(textSubmission.getId());
                textSubmission.setBlocks(textBlocks);

                if (textSubmission.isSubmitted() && result.getCompletionDate() != null) {
                    List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
                    result.setFeedbacks(assessments);
                }

                if (!authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
                    result.setAssessor(null);
                }
            }

            participation.addSubmission(textSubmission);
        }

        if (!(authCheckService.isAtLeastInstructorForExercise(textExercise, user) || participation.isOwnedBy(user))) {
            participation.setParticipant(null);
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * POST /text-exercises/{exerciseId}/trigger-automatic-assessment: trigger automatic assessment
     * (clustering task) for given exercise id As the clustering can be performed on a different
     * node, this will always return 200, despite an error could occur on the other node.
     *
     * @param exerciseId id of the exercised that for which the automatic assessment should be
     *                   triggered
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/text-exercises/{exerciseId}/trigger-automatic-assessment")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> triggerAutomaticAssessment(@PathVariable Long exerciseId) {
        instanceMessageSendService.sendTextExerciseInstantClustering(exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * Search for all text exercises by title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("/text-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR, ADMIN')")
    public ResponseEntity<SearchResultPageDTO<TextExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(textExerciseService.getAllOnPageWithSize(search, user));
    }

    /**
     * POST /text-exercises/import: Imports an existing text exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and Dates. Referenced
     * entities will get cloned and assigned a new id.
     * Exercise)}
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the
     *                         imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     * or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("/text-exercises/import/{sourceExerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextExercise> importExercise(@PathVariable long sourceExerciseId, @RequestBody TextExercise importedExercise) throws URISyntaxException {
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            return badRequest();
        }
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        if (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null) {
            log.debug("REST request to import text exercise {} into exercise group {}", sourceExerciseId, importedExercise.getExerciseGroup().getId());
            if (!authCheckService.isAtLeastInstructorInCourse(importedExercise.getExerciseGroup().getExam().getCourse(), user)) {
                log.debug("User {} is not allowed to import exercises into course of exercise group {}", user.getId(), importedExercise.getExerciseGroup().getId());
                return forbidden();
            }
        }
        else {
            log.debug("REST request to import text exercise with {} into course {}", sourceExerciseId, importedExercise.getCourseViaExerciseGroupOrCourseMember().getId());
            if (!authCheckService.isAtLeastInstructorInCourse(importedExercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                log.debug("User {} is not allowed to import exercises into course {}", user.getId(), importedExercise.getCourseViaExerciseGroupOrCourseMember().getId());
                return forbidden();
            }
        }

        if (originalTextExercise.getCourseViaExerciseGroupOrCourseMember() == null) {
            if (!authCheckService.isAtLeastInstructorInCourse(originalTextExercise.getExerciseGroup().getExam().getCourse(), user)) {
                log.debug("User {} is not allowed to import exercises from exercise group {}", user.getId(), originalTextExercise.getExerciseGroup().getId());
                return forbidden();
            }
        }
        else if (originalTextExercise.getExerciseGroup() == null) {
            if (!authCheckService.isAtLeastInstructorInCourse(originalTextExercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                log.debug("User {} is not allowed to import exercises from course {}", user.getId(), originalTextExercise.getCourseViaExerciseGroupOrCourseMember().getId());
                return forbidden();
            }
        }

        final var newTextExercise = textExerciseImportService.importTextExercise(originalTextExercise, importedExercise);
        textExerciseRepository.save((TextExercise) newTextExercise);
        return ResponseEntity.created(new URI("/api/text-exercises/" + newTextExercise.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newTextExercise.getId().toString())).body((TextExercise) newTextExercise);
    }

    /**
     * POST /text-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("/text-exercises/{exerciseId}/export-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {

        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise)) {
            return forbidden();
        }

        // TAs are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants() && !authCheckService.isAtLeastInstructorInCourse(textExercise.getCourseViaExerciseGroupOrCourseMember(), null)) {
            return forbidden();
        }

        try {
            Optional<File> zipFile = textSubmissionExportService.exportStudentSubmissions(exerciseId, submissionExportOptions);

            if (zipFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "nosubmissions", "No existing user was specified or no submission exists."))
                        .body(null);
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile.get()));
            return ResponseEntity.ok().contentLength(zipFile.get().length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.get().getName())
                    .body(resource);

        }
        catch (IOException e) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }
    }

    /**
     * GET /check-plagiarism : Use JPlag to detect plagiarism in text exercises
     *
     * @param exerciseId ID of the exercise for which to detect plagiarism
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore consider only submissions whose score is greater or equal to this value
     * @param minimumSize consider only submissions whose size is greater or equal to this value
     * @return the result of the JPlag plagiarism detection
     */
    @GetMapping("/text-exercises/{exerciseId}/check-plagiarism")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TextPlagiarismResult> checkPlagiarism(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore,
            @RequestParam int minimumSize) throws ExitException {
        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);

        if (!authCheckService.isAtLeastInstructorForExercise(textExercise)) {
            return forbidden();
        }

        TextPlagiarismResult result = textPlagiarismDetectionService.checkPlagiarism(textExercise, similarityThreshold, minimumScore, minimumSize);
        return ResponseEntity.ok(result);
    }

}
