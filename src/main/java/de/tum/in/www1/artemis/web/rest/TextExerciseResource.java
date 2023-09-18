package de.tum.in.www1.artemis.web.rest;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.export.TextSubmissionExportService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationScheduleService;
import de.tum.in.www1.artemis.service.plagiarism.TextPlagiarismDetectionService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("api/")
public class TextExerciseResource {

    private final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

    private static final String ENTITY_NAME = "textExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseService textExerciseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final TextExerciseImportService textExerciseImportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ParticipationRepository participationRepository;

    private final ResultRepository resultRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final TextPlagiarismDetectionService textPlagiarismDetectionService;

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository, TextExerciseService textExerciseService, FeedbackRepository feedbackRepository,
            ExerciseDeletionService exerciseDeletionService, PlagiarismResultRepository plagiarismResultRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, CourseService courseService, StudentParticipationRepository studentParticipationRepository,
            ParticipationRepository participationRepository, ResultRepository resultRepository, TextExerciseImportService textExerciseImportService,
            TextSubmissionExportService textSubmissionExportService, ExampleSubmissionRepository exampleSubmissionRepository, ExerciseService exerciseService,
            GradingCriterionRepository gradingCriterionRepository, TextBlockRepository textBlockRepository, GroupNotificationScheduleService groupNotificationScheduleService,
            InstanceMessageSendService instanceMessageSendService, TextPlagiarismDetectionService textPlagiarismDetectionService, CourseRepository courseRepository,
            ChannelService channelService, ChannelRepository channelRepository) {
        this.feedbackRepository = feedbackRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseService = textExerciseService;
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exerciseService = exerciseService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.textPlagiarismDetectionService = textPlagiarismDetectionService;
        this.courseRepository = courseRepository;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
    }

    /**
     * POST /text-exercises : Create a new textExercise.
     *
     * @param textExercise the textExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textExercise, or
     *         with status 400 (Bad Request) if the textExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> createTextExercise(@RequestBody TextExercise textExercise) throws URISyntaxException {
        log.debug("REST request to save TextExercise : {}", textExercise);
        if (textExercise.getId() != null) {
            throw new BadRequestAlertException("A new textExercise cannot already have an ID", ENTITY_NAME, "idExists");
        }

        if (textExercise.getTitle() == null) {
            throw new BadRequestAlertException("A new textExercise needs a title", ENTITY_NAME, "missingtitle");
        }
        // validates general settings: points, dates
        textExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        textExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);
        // Check that the user is authorized to create the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        TextExercise result = textExerciseRepository.save(textExercise);

        channelService.createExerciseChannel(result, Optional.ofNullable(textExercise.getChannelName()));
        instanceMessageSendService.sendTextExerciseSchedule(result.getId());
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(textExercise);
        return ResponseEntity.created(new URI("/api/text-exercises/" + result.getId())).body(result);
    }

    /**
     * PUT /text-exercises : Updates an existing textExercise.
     *
     * @param textExercise     the textExercise to update
     * @param notificationText about the text exercise update that should be displayed for the
     *                             student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated textExercise, or
     *         with status 400 (Bad Request) if the textExercise is not valid, or with status 500 (Internal
     *         Server Error) if the textExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody TextExercise textExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update TextExercise : {}", textExercise);
        if (textExercise.getId() == null) {
            return createTextExercise(textExercise);
        }
        // validates general settings: points, dates
        textExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        textExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check
        final TextExercise textExerciseBeforeUpdate = textExerciseRepository.findByIdElseThrow(textExercise.getId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExerciseBeforeUpdate, user);

        // Forbid changing the course the exercise belongs to.
        if (!Objects.equals(textExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId(), textExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(textExercise, textExerciseBeforeUpdate, ENTITY_NAME);

        channelService.updateExerciseChannel(textExerciseBeforeUpdate, textExercise);

        TextExercise updatedTextExercise = textExerciseRepository.save(textExercise);
        exerciseService.logUpdate(updatedTextExercise, updatedTextExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(textExerciseBeforeUpdate, updatedTextExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedTextExercise, textExerciseBeforeUpdate.getDueDate());
        instanceMessageSendService.sendTextExerciseSchedule(updatedTextExercise.getId());
        exerciseService.checkExampleSubmissions(updatedTextExercise);
        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(textExerciseBeforeUpdate, updatedTextExercise, notificationText);
        return ResponseEntity.ok(updatedTextExercise);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId id of the course of which all the exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of textExercises in body
     */
    @GetMapping("courses/{courseId}/text-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<TextExercise>> getTextExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<TextExercise> exercises = textExerciseRepository.findByCourseIdWithCategories(courseId);
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
     * GET /text-exercises/:id : get the "id" textExercise.
     *
     * @param exerciseId the id of the textExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textExercise, or with
     *         status 404 (Not Found)
     */
    @GetMapping("text-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<TextExercise> getTextExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get TextExercise : {}", exerciseId);
        var textExercise = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("TextExercise", exerciseId));

        // If the exercise belongs to an exam, only editors, instructors and admins are allowed to access it
        if (textExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        }
        else {
            // in courses, also tutors can access the exercise
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, textExercise, null);
        }
        if (textExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(textExercise.getId());
            if (channel != null) {
                textExercise.setChannelName(channel.getName());
            }
        }

        Set<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllWithResultByExerciseId(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        textExercise.setGradingCriteria(gradingCriteria);
        textExercise.setExampleSubmissions(exampleSubmissions);

        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, textExercise);
        return ResponseEntity.ok().body(textExercise);
    }

    /**
     * DELETE /text-exercises/:exerciseId : delete the "exerciseId" textExercise.
     *
     * @param exerciseId the id of the textExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("text-exercises/{exerciseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteTextExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete TextExercise : {}", exerciseId);
        var textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, textExercise, user);
        // NOTE: we use the exerciseDeletionService here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(textExercise, textExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false, false);
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
    @GetMapping("text-editor/{participationId}")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getDataForTextEditor(@PathVariable Long participationId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = studentParticipationRepository.findByIdWithLegalSubmissionsResultsFeedbackElseThrow(participationId);
        if (!(participation.getExercise() instanceof TextExercise textExercise)) {
            throw new BadRequestAlertException("The exercise of the participation is not a text exercise.", ENTITY_NAME, "wrongExerciseType");
        }

        // users can only see their own submission (to prevent cheating), TAs, instructors and admins can see all answers
        if (!authCheckService.isOwnerOfParticipation(participation, user) && !authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            throw new AccessForbiddenException();
        }

        // Exam exercises cannot be seen by students between the endDate and the publishResultDate
        if (!authCheckService.isAllowedToGetExamResult(textExercise, participation, user)) {
            throw new AccessForbiddenException();
        }

        // if no results, check if there are really no results or the relation to results was not updated yet
        if (participation.getResults().isEmpty()) {
            List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
            participation.setResults(new HashSet<>(results));
        }

        Optional<Submission> optionalSubmission = participation.findLatestSubmission();
        participation.setSubmissions(new HashSet<>());

        if (optionalSubmission.isPresent()) {
            TextSubmission textSubmission = (TextSubmission) optionalSubmission.get();

            // set reference to participation to null, since we are already inside a participation
            textSubmission.setParticipation(null);

            if (!ExerciseDateService.isAfterAssessmentDueDate(textExercise)) {
                textSubmission.setResults(Collections.emptyList());
                participation.setResults(Collections.emptySet());
            }

            Result result = textSubmission.getLatestResult();
            if (result != null) {
                // Load TextBlocks for the Submission. They are needed to display the Feedback in the client.
                final var textBlocks = textBlockRepository.findAllBySubmissionId(textSubmission.getId());
                textSubmission.setBlocks(textBlocks);

                if (textSubmission.isSubmitted() && result.getCompletionDate() != null) {
                    List<Feedback> assessments = feedbackRepository.findByResult(result);
                    result.setFeedbacks(assessments);
                }

                if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
                    result.filterSensitiveInformation();
                }

                // only send the one latest result to the client
                textSubmission.setResults(List.of(result));
                participation.setResults(Set.of(result));
            }

            participation.addSubmission(textSubmission);
        }

        if (!(authCheckService.isAtLeastInstructorForExercise(textExercise, user) || participation.isOwnedBy(user))) {
            participation.filterSensitiveInformation();
        }

        textExercise.filterSensitiveInformation();
        if (textExercise.isExamExercise()) {
            textExercise.getExamViaExerciseGroupOrCourseMember().setCourse(null);
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * Search for all text exercises by id, title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<TextExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search, @RequestParam(defaultValue = "true") boolean isCourseFilter,
            @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(textExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    /**
     * POST /text-exercises/import: Imports an existing text exercise into an existing course
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
    @PostMapping("text-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> importExercise(@PathVariable long sourceExerciseId, @RequestBody TextExercise importedExercise) throws URISyntaxException {
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity("Text Exercise");
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalTextExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();

        final var newTextExercise = textExerciseImportService.importTextExercise(originalTextExercise, importedExercise);
        textExerciseRepository.save(newTextExercise);
        return ResponseEntity.created(new URI("/api/text-exercises/" + newTextExercise.getId())).body(newTextExercise);
    }

    /**
     * POST /text-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("text-exercises/{exerciseId}/export-submissions")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, textExercise, null);

        // TAs are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, textExercise.getCourseViaExerciseGroupOrCourseMember(), null);
        }

        File zipFile = textSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFile);
    }

    /**
     * GET /text-exercises/{exerciseId}/plagiarism-result
     * <p>
     * Return the latest plagiarism result or null, if no plagiarism was detected for this exercise
     * yet.
     *
     * @param exerciseId ID of the text exercise for which the plagiarism result should be returned
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the
     *         parameters are invalid
     */
    @GetMapping("text-exercises/{exerciseId}/plagiarism-result")
    @EnforceAtLeastEditor
    public ResponseEntity<TextPlagiarismResult> getPlagiarismResult(@PathVariable long exerciseId) {
        log.debug("REST request to get the latest plagiarism result for the text exercise with id: {}", exerciseId);
        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        var plagiarismResult = (TextPlagiarismResult) plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return ResponseEntity.ok(plagiarismResult);
    }

    /**
     * GET /text-exercises/{exerciseId}/check-plagiarism
     * <p>
     * Start the automated plagiarism detection for the given exercise and return its result.
     *
     * @param exerciseId          ID of the exercise for which to detect plagiarism
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @param minimumSize         consider only submissions whose size is greater or equal to this value
     * @return the ResponseEntity with status 200 (OK) and the list of at most 500 pair-wise submissions with a similarity above the given threshold (e.g. 50%).
     */
    @GetMapping("text-exercises/{exerciseId}/check-plagiarism")
    @FeatureToggle(Feature.PlagiarismChecks)
    @EnforceAtLeastEditor
    public ResponseEntity<TextPlagiarismResult> checkPlagiarism(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore,
            @RequestParam int minimumSize) throws ExitException {
        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        log.info("Start textPlagiarismDetectionService.checkPlagiarism for exercise {}", exerciseId);
        long start = System.nanoTime();
        var plagiarismResult = textPlagiarismDetectionService.checkPlagiarism(textExercise, similarityThreshold, minimumScore, minimumSize);
        log.info("Finished textPlagiarismDetectionService.checkPlagiarism for exercise {} with {} comparisons in {}", exerciseId, plagiarismResult.getComparisons().size(),
                TimeLogUtil.formatDurationFrom(start));
        // TODO: limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(100);
        log.info("Limited number of comparisons to {} to avoid performance issues when saving to database", plagiarismResult.getComparisons().size());
        start = System.nanoTime();
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);
        log.info("Finished plagiarismResultRepository.savePlagiarismResultAndRemovePrevious call in {}", TimeLogUtil.formatDurationFrom(start));
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return ResponseEntity.ok(plagiarismResult);
    }

    /**
     * PUT /text-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing textExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param textExercise                                the textExercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated textExercise, or
     *         with status 400 (Bad Request) if the textExercise is not valid, or with status 409 (Conflict)
     *         if given exerciseId is not same as in the object of the request body, or with status 500
     *         (Internal Server Error) if the textExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("text-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> reEvaluateAndUpdateTextExercise(@PathVariable long exerciseId, @RequestBody TextExercise textExercise,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) throws URISyntaxException {
        log.debug("REST request to re-evaluate TextExercise : {}", textExercise);

        // check that the exercise exists for given id
        textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);

        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, textExercise);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(textExercise, deleteFeedbackAfterGradingInstructionUpdate);

        return updateTextExercise(textExercise, null);
    }
}
