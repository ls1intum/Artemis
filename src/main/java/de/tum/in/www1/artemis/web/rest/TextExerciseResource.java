package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.web.rest.plagiarism.PlagiarismResultResponseBuilder.buildPlagiarismResultResponse;
import static java.util.Collections.emptySet;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.GradingCriterionRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.ExerciseDeletionService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.TextExerciseImportService;
import de.tum.in.www1.artemis.service.TextExerciseService;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaModuleService;
import de.tum.in.www1.artemis.service.export.TextSubmissionExportService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationScheduleService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismDetectionConfigHelper;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismDetectionService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismResultDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

/**
 * REST controller for managing TextExercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TextExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

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

    private final PlagiarismDetectionService plagiarismDetectionService;

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final Optional<AthenaModuleService> athenaModuleService;

    private final CompetencyProgressService competencyProgressService;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository, TextExerciseService textExerciseService, FeedbackRepository feedbackRepository,
            ExerciseDeletionService exerciseDeletionService, PlagiarismResultRepository plagiarismResultRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, CourseService courseService, StudentParticipationRepository studentParticipationRepository,
            ParticipationRepository participationRepository, ResultRepository resultRepository, TextExerciseImportService textExerciseImportService,
            TextSubmissionExportService textSubmissionExportService, ExampleSubmissionRepository exampleSubmissionRepository, ExerciseService exerciseService,
            GradingCriterionRepository gradingCriterionRepository, TextBlockRepository textBlockRepository, GroupNotificationScheduleService groupNotificationScheduleService,
            InstanceMessageSendService instanceMessageSendService, PlagiarismDetectionService plagiarismDetectionService, CourseRepository courseRepository,
            ChannelService channelService, ChannelRepository channelRepository, Optional<AthenaModuleService> athenaModuleService,
            CompetencyProgressService competencyProgressService) {
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
        this.plagiarismDetectionService = plagiarismDetectionService;
        this.courseRepository = courseRepository;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.athenaModuleService = athenaModuleService;
        this.competencyProgressService = competencyProgressService;
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

        // Check that only allowed athena modules are used
        athenaModuleService.ifPresentOrElse(ams -> ams.checkHasAccessToAthenaModule(textExercise, course, ENTITY_NAME), () -> textExercise.setFeedbackSuggestionModule(null));

        TextExercise result = textExerciseRepository.save(textExercise);

        channelService.createExerciseChannel(result, Optional.ofNullable(textExercise.getChannelName()));
        instanceMessageSendService.sendTextExerciseSchedule(result.getId());
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(textExercise);
        competencyProgressService.updateProgressByLearningObjectAsync(result);

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
        final TextExercise textExerciseBeforeUpdate = textExerciseRepository.findWithEagerCompetenciesByIdElseThrow(textExercise.getId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExerciseBeforeUpdate, user);

        // Forbid changing the course the exercise belongs to.
        if (!Objects.equals(textExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId(), textExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(textExercise, textExerciseBeforeUpdate, ENTITY_NAME);

        // Check that only allowed athena modules are used
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExerciseBeforeUpdate);
        athenaModuleService.ifPresentOrElse(ams -> ams.checkHasAccessToAthenaModule(textExercise, course, ENTITY_NAME), () -> textExercise.setFeedbackSuggestionModule(null));
        // Changing Athena module after the due date has passed is not allowed
        athenaModuleService.ifPresent(ams -> ams.checkValidAthenaModuleChange(textExerciseBeforeUpdate, textExercise, ENTITY_NAME));

        channelService.updateExerciseChannel(textExerciseBeforeUpdate, textExercise);

        TextExercise updatedTextExercise = textExerciseRepository.save(textExercise);
        exerciseService.logUpdate(updatedTextExercise, updatedTextExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(textExerciseBeforeUpdate, updatedTextExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedTextExercise, textExerciseBeforeUpdate.getDueDate());
        instanceMessageSendService.sendTextExerciseSchedule(updatedTextExercise.getId());
        exerciseService.checkExampleSubmissions(updatedTextExercise);
        exerciseService.notifyAboutExerciseChanges(textExerciseBeforeUpdate, updatedTextExercise, notificationText);

        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(textExerciseBeforeUpdate, Optional.of(textExercise));

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
            Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
            exercise.setGradingCriteria(gradingCriteria);
        }
        return ResponseEntity.ok().body(exercises);
    }

    private Optional<TextExercise> findTextExercise(Long exerciseId, boolean includePlagiarismDetectionConfig) {
        if (includePlagiarismDetectionConfig) {
            var textExercise = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(exerciseId);
            textExercise.ifPresent(it -> PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(it, textExerciseRepository));
            return textExercise;
        }
        return textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(exerciseId);
    }

    /**
     * GET /text-exercises/:id : get the "id" textExercise.
     *
     * @param exerciseId                    the id of the textExercise to retrieve
     * @param withPlagiarismDetectionConfig boolean flag whether to include the plagiarism detection config of the exercise
     * @return the ResponseEntity with status 200 (OK) and with body the textExercise, or with
     *         status 404 (Not Found)
     */
    @GetMapping("text-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<TextExercise> getTextExercise(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean withPlagiarismDetectionConfig) {
        log.debug("REST request to get TextExercise : {}", exerciseId);
        var textExercise = findTextExercise(exerciseId, withPlagiarismDetectionConfig).orElseThrow(() -> new EntityNotFoundException("TextExercise", exerciseId));

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
        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
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
                participation.setResults(emptySet());
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
            textExercise.getExam().setCourse(null);
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
    public ResponseEntity<SearchResultPageDTO<TextExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
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
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalTextExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();

        // Athena: Check that only allowed athena modules are used, if not we catch the exception and disable feedback suggestions for the imported exercise
        // If Athena is disabled and the service is not present, we also disable feedback suggestions
        try {
            athenaModuleService.ifPresentOrElse(ams -> ams.checkHasAccessToAthenaModule(importedExercise, importedExercise.getCourseViaExerciseGroupOrCourseMember(), ENTITY_NAME),
                    () -> importedExercise.setFeedbackSuggestionModule(null));
        }
        catch (BadRequestAlertException e) {
            importedExercise.setFeedbackSuggestionModule(null);
        }

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
    public ResponseEntity<PlagiarismResultDTO<TextPlagiarismResult>> getPlagiarismResult(@PathVariable long exerciseId) {
        log.debug("REST request to get the latest plagiarism result for the text exercise with id: {}", exerciseId);
        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        var plagiarismResult = (TextPlagiarismResult) plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return buildPlagiarismResultResponse(plagiarismResult);
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
    public ResponseEntity<PlagiarismResultDTO<TextPlagiarismResult>> checkPlagiarism(@PathVariable long exerciseId, @RequestParam int similarityThreshold,
            @RequestParam int minimumScore, @RequestParam int minimumSize) throws ExitException {
        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);

        long start = System.nanoTime();
        log.info("Started manual plagiarism checks for text exercise: exerciseId={}.", exerciseId);
        PlagiarismDetectionConfigHelper.updateWithTemporaryParameters(textExercise, similarityThreshold, minimumScore, minimumSize);
        var plagiarismResult = plagiarismDetectionService.checkTextExercise(textExercise);
        log.info("Finished manual plagiarism checks for text exercise: exerciseId={}, elapsed={}.", exerciseId, TimeLogUtil.formatDurationFrom(start));
        return buildPlagiarismResultResponse(plagiarismResult);
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
