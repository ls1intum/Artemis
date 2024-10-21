package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.core.exception.QuizJoinException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.CourseService;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizAction;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizMessagingService;
import de.tum.cit.aet.artemis.quiz.service.QuizResultService;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;

/**
 * REST controller for managing QuizExercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class QuizExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizSubmissionService quizSubmissionService;

    private final QuizResultService quizResultService;

    private final QuizExerciseService quizExerciseService;

    private final QuizMessagingService quizMessagingService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ExamDateService examDateService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final QuizStatisticService quizStatisticService;

    private final QuizExerciseImportService quizExerciseImportService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final QuizBatchService quizBatchService;

    private final QuizBatchRepository quizBatchRepository;

    private final FileService fileService;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final CompetencyProgressService competencyProgressService;

    public QuizExerciseResource(QuizExerciseService quizExerciseService, QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository,
            UserRepository userRepository, CourseService courseService, ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService,
            ExamDateService examDateService, InstanceMessageSendService instanceMessageSendService, QuizStatisticService quizStatisticService,
            QuizExerciseImportService quizExerciseImportService, AuthorizationCheckService authCheckService, GroupNotificationService groupNotificationService,
            GroupNotificationScheduleService groupNotificationScheduleService, StudentParticipationRepository studentParticipationRepository, QuizBatchService quizBatchService,
            QuizBatchRepository quizBatchRepository, FileService fileService, ChannelService channelService, ChannelRepository channelRepository,
            QuizSubmissionService quizSubmissionService, QuizResultService quizResultService, CompetencyProgressService competencyProgressService) {
        this.quizExerciseService = quizExerciseService;
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.examDateService = examDateService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizStatisticService = quizStatisticService;
        this.quizExerciseImportService = quizExerciseImportService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.quizBatchService = quizBatchService;
        this.quizBatchRepository = quizBatchRepository;
        this.fileService = fileService;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.quizResultService = quizResultService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Initialize the data binder for the quiz action enumeration
     *
     * @param binder the WebDataBinder for this controller
     */
    @InitBinder
    protected void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(QuizAction.class, new PropertyEditorSupport() {

            @Override
            public void setAsText(String text) {
                for (QuizAction action : QuizAction.values()) {
                    if (action.getValue().equals(text)) {
                        setValue(action);
                        return;
                    }
                }
                throw new IllegalArgumentException("Invalid value for QuizAction: " + text);
            }
        });
    }

    /**
     * POST /quiz-exercises : Create a new quizExercise.
     *
     * @param quizExercise the quizExercise to create
     * @param files        the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "quiz-exercises", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<QuizExercise> createQuizExercise(@RequestPart("exercise") QuizExercise quizExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws URISyntaxException, IOException {
        log.info("REST request to create QuizExercise : {}", quizExercise);
        if (quizExercise.getId() != null) {
            throw new BadRequestAlertException("A new quizExercise cannot already have an ID", ENTITY_NAME, "idExists");
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see below in update Quiz)
            throw new BadRequestAlertException("The quiz exercise is invalid", ENTITY_NAME, "invalidQuiz");
        }

        quizExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        quizExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        quizExerciseService.handleDndQuizFileCreation(quizExercise, files);

        QuizExercise result = quizExerciseService.save(quizExercise);

        channelService.createExerciseChannel(result, Optional.ofNullable(quizExercise.getChannelName()));

        competencyProgressService.updateProgressByLearningObjectAsync(result);

        return ResponseEntity.created(new URI("/api/quiz-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /quiz-exercises/:exerciseId : Updates an existing quizExercise.
     *
     * @param exerciseId       the id of the quizExercise to save
     * @param quizExercise     the quizExercise to update
     * @param files            the new files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @param notificationText about the quiz exercise update that should be displayed to the student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with status 500
     *         (Internal Server Error) if the quizExercise couldn't be updated
     */
    @PutMapping(value = "quiz-exercises/{exerciseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<QuizExercise> updateQuizExercise(@PathVariable Long exerciseId, @RequestPart("exercise") QuizExercise quizExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, @RequestParam(value = "notificationText", required = false) String notificationText)
            throws IOException {
        log.info("REST request to update quiz exercise : {}", quizExercise);
        quizExercise.setId(exerciseId);

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see above in create Quiz)
            throw new BadRequestAlertException("The quiz exercise is invalid", ENTITY_NAME, "invalidQuiz");
        }

        quizExercise.validateGeneralSettings();

        // Valid exercises have set either a course or an exerciseGroup
        quizExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        final var originalQuiz = quizExerciseRepository.findByIdWithQuestionsAndCompetenciesElseThrow(exerciseId);

        var user = userRepository.getUserWithGroupsAndAuthorities();

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(quizExercise, originalQuiz, ENTITY_NAME);

        // check if quiz is has already started
        var batches = quizBatchRepository.findAllByQuizExercise(originalQuiz);
        if (batches.stream().anyMatch(QuizBatch::isStarted)) {
            throw new BadRequestAlertException("The quiz has already started. Use the re-evaluate endpoint to make retroactive corrections.", ENTITY_NAME, "quizHasStarted");
        }

        quizExercise.reconnectJSONIgnoreAttributes();

        // don't allow changing batches except in synchronized mode as the client doesn't have the full list and saving the exercise could otherwise end up deleting a bunch
        if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED || quizExercise.getQuizBatches() == null || quizExercise.getQuizBatches().size() > 1) {
            quizExercise.setQuizBatches(batches);
        }

        quizExerciseService.handleDndQuizFileUpdates(quizExercise, originalQuiz, files);

        Channel updatedChannel = channelService.updateExerciseChannel(originalQuiz, quizExercise);

        exerciseService.reconnectCompetencyExerciseLinks(quizExercise);

        quizExercise = quizExerciseService.save(quizExercise);
        exerciseService.logUpdate(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(originalQuiz, quizExercise, notificationText);
        if (updatedChannel != null) {
            quizExercise.setChannelName(updatedChannel.getName());
        }
        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(originalQuiz, Optional.of(quizExercise));

        return ResponseEntity.ok(quizExercise);
    }

    /**
     * GET /courses/:courseId/quiz-exercises : get all the exercises.
     *
     * @param courseId id of the course of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of quiz exercises in body
     */
    @GetMapping("courses/{courseId}/quiz-exercises")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<List<QuizExercise>> getQuizExercisesForCourse(@PathVariable Long courseId) {
        log.info("REST request to get all quiz exercises for the course with id : {}", courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var quizExercises = quizExerciseRepository.findByCourseIdWithCategories(courseId);

        for (QuizExercise quizExercise : quizExercises) {
            quizExercise.setQuizQuestions(null);
            // not required in the returned json body
            quizExercise.setStudentParticipations(null);
            quizExercise.setCourse(null);
            setQuizBatches(user, quizExercise);
        }

        return ResponseEntity.ok(quizExercises);
    }

    /**
     * GET /:examId/quiz-exercises : get all the quiz exercises of an exam.
     *
     * @param examId id of the exam of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of quiz exercises in body
     */
    @GetMapping("exams/{examId}/quiz-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<List<QuizExercise>> getQuizExercisesForExam(@PathVariable Long examId) {
        log.info("REST request to get all quiz exercises for the exam with id : {}", examId);
        List<QuizExercise> quizExercises = quizExerciseRepository.findByExamId(examId);
        Course course = quizExercises.getFirst().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        for (QuizExercise quizExercise : quizExercises) {
            quizExercise.setQuizQuestions(null);
            // not required in the returned json body
            quizExercise.setStudentParticipations(null);
            quizExercise.setCourse(null);
            quizExercise.setExerciseGroup(null);
        }
        return ResponseEntity.ok(quizExercises);
    }

    /**
     * GET /quiz-exercises/:quizExerciseId : get the quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("quiz-exercises/{quizExerciseId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long quizExerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.info("REST request to get quiz exercise : {}", quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(quizExerciseId);
        if (quizExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, quizExercise, user);
            studentParticipationRepository.checkTestRunsExist(quizExercise);
        }
        if (quizExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(quizExercise.getId());
            if (channel != null) {
                quizExercise.setChannelName(channel.getName());
            }
        }
        setQuizBatches(user, quizExercise);
        return ResponseEntity.ok(quizExercise);
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/recalculate-statistics : recalculate all statistics in case something went wrong with them
     *
     * @param quizExerciseId the id of the quizExercise for which the statistics should be recalculated
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("quiz-exercises/{quizExerciseId}/recalculate-statistics")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExercise> recalculateStatistics(@PathVariable Long quizExerciseId) {
        log.info("REST request to recalculate quiz statistics : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        quizStatisticService.recalculateStatistics(quizExercise);
        // fetch the quiz exercise again to make sure the latest changes are included
        return ResponseEntity.ok(quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId()));
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/for-student : get the quizExercise with a particular batch. (information filtered for students)
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("quiz-exercises/{quizExerciseId}/for-student")
    @EnforceAtLeastStudent
    public ResponseEntity<QuizExercise> getQuizExerciseForStudent(@PathVariable Long quizExerciseId) {
        log.info("REST request to get quiz exercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, user)) {
            throw new AccessForbiddenException();
        }
        quizExercise.setQuizBatches(null); // remove proxy and load batches only if required
        var batch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin());
        log.info("Found batch {} for user {}", batch.orElse(null), user.getLogin());
        quizExercise.setQuizBatches(batch.stream().collect(Collectors.toSet()));
        // filter out information depending on quiz state
        quizExercise.applyAppropriateFilterForStudents(batch.orElse(null));

        return ResponseEntity.ok(quizExercise);
    }

    /**
     * POST /quiz-exercises/:quizExerciseId/join : add a student to a particular batch for participating in it and if in INDIVIDUAL mode create the batch to join
     *
     * @param quizExerciseId the id of the quizExercise to which the batch to join belongs
     * @param joinRequest    DTO with the password for the batch to join; unused for quizzes in INDIVIDUAL mode
     * @return the ResponseEntity with status 200 (OK) and with body the quizBatch that was joined
     */
    @PostMapping("quiz-exercises/{quizExerciseId}/join")
    @EnforceAtLeastStudent
    public ResponseEntity<QuizBatch> joinBatch(@PathVariable Long quizExerciseId, @RequestBody QuizBatchJoinDTO joinRequest) {
        log.info("REST request to join quiz batch : {}, {}", quizExerciseId, joinRequest);
        var quizExercise = quizExerciseRepository.findByIdElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, user) || !quizExercise.isQuizStarted() || quizExercise.isQuizEnded()) {
            throw new AccessForbiddenException();
        }
        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("Students cannot join quiz batches for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }

        try {
            var batch = quizBatchService.joinBatch(quizExercise, user, joinRequest.password());
            return ResponseEntity.ok(batch);
        }
        catch (QuizJoinException ex) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", ex.getError(), ex.getMessage())).build();
        }
    }

    /**
     * POST /quiz-exercises/:quizExerciseId/add-batch : add a new batch to the exercise for batched running
     *
     * @param quizExerciseId the id of the quizExercise to add the batch to
     * @return the ResponseEntity with status 200 (OK) and with body the new batch
     */
    @PutMapping("quiz-exercises/{quizExerciseId}/add-batch")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizBatch> addBatch(@PathVariable Long quizExerciseId) {
        log.info("REST request to add quiz batch : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithBatchesElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("Batches cannot be created for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }

        // TODO: quiz cleanup: it should be possible to limit the number of batches a tutor can create

        var quizBatch = quizBatchService.createBatch(quizExercise, user);
        quizBatch = quizBatchService.save(quizBatch);

        return ResponseEntity.ok(quizBatch);
    }

    /**
     * TODO: URL should be /quiz-exercises/batches/:batchId/join or smth for clarity
     * POST /quiz-exercises/:quizBatchId/start-batch : start a particular batch of the quiz
     *
     * @param quizBatchId the id of the quizBatch to start
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("quiz-exercises/{quizBatchId}/start-batch")
    @EnforceAtLeastTutor
    public ResponseEntity<QuizBatch> startBatch(@PathVariable Long quizBatchId) {
        log.info("REST request to start quiz batch : {}", quizBatchId);
        QuizBatch batch = quizBatchRepository.findByIdElseThrow(quizBatchId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(batch.getQuizExercise().getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (!user.getId().equals(batch.getCreator())) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, quizExercise, user);
        }
        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("Batches cannot be started for exam exercises", ENTITY_NAME, "");
        }

        batch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, ZonedDateTime.now()));
        batch = quizBatchService.save(batch);

        // ensure that there is no scheduler that thinks the batch hasn't started yet
        instanceMessageSendService.sendQuizExerciseStartSchedule(quizExercise.getId());

        quizExercise.setQuizBatches(Set.of(batch));
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, batch, QuizAction.START_BATCH);

        return ResponseEntity.ok(batch);
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/:action : perform the specified action for the quiz now
     *
     * @param quizExerciseId the id of the quiz exercise to start
     * @param action         the action to perform on the quiz (allowed actions: "start-now", "set-visible", "open-for-practice")
     * @return the response entity with status 200 if quiz was started, appropriate error code otherwise
     */
    @PutMapping("quiz-exercises/{quizExerciseId}/{action}")
    @EnforceAtLeastEditorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExercise> performActionForQuizExercise(@PathVariable Long quizExerciseId, @PathVariable QuizAction action) {
        log.debug("REST request to perform action {} on quiz exercise {}", action, quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("These actions are not allowed for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }

        switch (action) {
            case START_NOW -> {
                // only synchronized quiz exercises can be started like this
                if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotSynchronized", "Quiz is not synchronized.")).build();
                }

                var quizBatch = quizBatchService.getOrCreateSynchronizedQuizBatch(quizExercise);
                // check if quiz hasn't already started
                if (quizBatch.isStarted()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyStarted", "Quiz has already started.")).build();
                }

                // set release date to now, truncated to seconds
                var now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                quizBatch.setStartTime(now);
                quizBatchRepository.save(quizBatch);
                if (quizExercise.getReleaseDate() != null && quizExercise.getReleaseDate().isAfter(now)) {
                    // preserve null and valid releaseDates for quiz start lifecycle event
                    quizExercise.setReleaseDate(now);
                }
                quizExercise.setDueDate(now.plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
            }
            case END_NOW -> {
                // editors may not end the quiz
                authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);
                // only synchronized quiz exercises can be started like this
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizSynchronized", "Quiz is synchronized."))
                            .build();
                }

                // set release date to now, truncated to seconds because the database only stores seconds
                quizExerciseService.endQuiz(quizExercise);
            }
            case SET_VISIBLE -> {
                // check if quiz is already visible
                if (quizExercise.isVisibleToStudents()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyVisible", "Quiz is already visible to students.")).build();
                }

                // set quiz to visible
                quizExercise.setReleaseDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            }
            case OPEN_FOR_PRACTICE -> {
                // check if quiz has ended
                if (!quizExercise.isQuizEnded()) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotEndedYet", "Quiz hasn't ended yet."))
                            .build();
                }
                // check if quiz is already open for practice
                if (quizExercise.isIsOpenForPractice()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyOpenForPractice", "Quiz is already open for practice."))
                            .build();
                }

                // set quiz to open for practice
                quizExercise.setIsOpenForPractice(true);
                groupNotificationService.notifyStudentGroupAboutExercisePractice(quizExercise);
            }
            case START_BATCH -> {
                // Use the start-batch endpoint for starting batches instead
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizBatchActionNotAllowed", "Action not allowed."))
                        .build();
            }
        }

        // save quiz exercise
        quizExercise = quizExerciseRepository.saveAndFlush(quizExercise);
        // reload the quiz exercise with questions and statistics to prevent problems with proxy objects
        quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());

        if (action == QuizAction.START_NOW) {
            // notify the instance message send service to send the quiz exercise start schedule (if necessary
            instanceMessageSendService.sendQuizExerciseStartSchedule(quizExercise.getId());
        }
        else if (action == QuizAction.END_NOW) {
            // when the instructor ends the quiz, calculate the results
            quizSubmissionService.calculateAllResults(quizExerciseId);
        }

        // get the batch for synchronized quiz exercises and start-now action; otherwise it doesn't matter
        var quizBatch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, "any").orElse(null);

        // notify websocket channel of changes to the quiz exercise
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, quizBatch, action);
        return new ResponseEntity<>(quizExercise, HttpStatus.OK);
    }

    /**
     * POST /quiz-exercises/{exerciseId}/evaluate : Evaluate the quiz exercise
     *
     * @param quizExerciseId the id of the quiz exercise
     * @return ResponseEntity void
     */
    @PostMapping("quiz-exercises/{quizExerciseId}/evaluate")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> evaluateQuizExercise(@PathVariable Long quizExerciseId) {
        log.debug("REST request to evaluate quiz exercise {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdElseThrow(quizExerciseId);
        if (!quizExercise.isQuizEnded()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotEndedYet", "Quiz hasn't ended yet.")).build();
        }

        quizResultService.evaluateQuizAndUpdateStatistics(quizExerciseId);
        log.debug("Evaluation of quiz exercise {} finished", quizExerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /quiz-exercises/:quizExerciseId : delete the "id" quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("quiz-exercises/{quizExerciseId}")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long quizExerciseId) {
        log.info("REST request to delete quiz exercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndCompetenciesElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        List<DragAndDropQuestion> dragAndDropQuestions = quizExercise.getQuizQuestions().stream().filter(question -> question instanceof DragAndDropQuestion)
                .map(question -> ((DragAndDropQuestion) question)).toList();
        List<String> backgroundImagePaths = dragAndDropQuestions.stream().map(DragAndDropQuestion::getBackgroundFilePath).toList();
        List<String> dragItemImagePaths = dragAndDropQuestions.stream().flatMap(question -> question.getDragItems().stream().map(DragItem::getPictureFilePath)).toList();
        List<Path> imagesToDelete = Stream.concat(backgroundImagePaths.stream(), dragItemImagePaths.stream()).map(path -> {
            if (path == null) {
                return null;
            }
            try {
                return FilePathService.actualPathForPublicPathOrThrow(URI.create(path));
            }
            catch (FilePathParsingException e) {
                // if the path is invalid, we can't delete it, but we don't want to fail the whole deletion
                log.warn("Could not find file {} for deletion", path);
                return null;
            }
        }).filter(Objects::nonNull).toList();

        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(quizExerciseId, false, false);
        quizExerciseService.cancelScheduledQuiz(quizExerciseId);

        fileService.deleteFiles(imagesToDelete);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, quizExercise.getTitle())).build();
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/re-evaluate : Re-evaluates an existing quizExercise.
     * <p>
     * 1. reset not allowed changes and set flag updateResultsAndStatistics if a recalculation of results and statistics is necessary
     * 2. save changed quizExercise
     * 3. if flag is set: -> change results if an answer or a question is set invalid -> recalculate statistics and results and save them.
     *
     * @param quizExerciseId the quiz id for the quiz that should be re-evaluated
     * @param quizExercise   the quizExercise to re-evaluate
     * @param files          the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @return the ResponseEntity with status 200 (OK) and with body the re-evaluated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with
     *         status 500 (Internal Server Error) if the quizExercise couldn't be re-evaluated
     */
    @PutMapping(value = "quiz-exercises/{quizExerciseId}/re-evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExercise> reEvaluateQuizExercise(@PathVariable Long quizExerciseId, @RequestPart("exercise") QuizExercise quizExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
        log.info("REST request to re-evaluate quiz exercise : {}", quizExerciseId);
        QuizExercise originalQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);

        if (originalQuizExercise.isExamExercise()) {
            // Re-evaluation of an exam quiz is only possible if all students finished their exam
            ZonedDateTime latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(originalQuizExercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                throw new BadRequestAlertException("The exam of the quiz exercise has not ended yet. Re-evaluation is only allowed after an exam has ended.", ENTITY_NAME,
                        "examOfQuizExerciseNotEnded");
            }
        }
        else if (!originalQuizExercise.isQuizEnded()) {
            throw new BadRequestAlertException("The quiz exercise has not ended yet. Re-evaluation is only allowed after a quiz has ended.", ENTITY_NAME, "quizExerciseNotEnded");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();

        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;

        quizExercise = quizExerciseService.reEvaluate(quizExercise, originalQuizExercise, nullsafeFiles);
        exerciseService.logUpdate(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);

        quizExercise.validateScoreSettings();
        return ResponseEntity.ok().body(quizExercise);
    }

    /**
     * Search for all quiz exercises by id, title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("quiz-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<QuizExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(quizExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    /**
     * POST /quiz-exercises/import: Imports an existing quiz exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates. Referenced
     * entities will get cloned and assigned a new id.
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @param files            the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     *         or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping(value = "quiz-exercises/import/{sourceExerciseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<QuizExercise> importExercise(@PathVariable long sourceExerciseId, @RequestPart("exercise") QuizExercise importedExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws URISyntaxException, IOException {
        log.info("REST request to import from quiz exercise : {}", sourceExerciseId);
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }

        // Valid exercises have set either a course or an exerciseGroup
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(importedExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        if (!importedExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see above in create Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        List<MultipartFile> nullsafeFiles = files != null ? files : new ArrayList<>();

        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();
        quizExerciseService.validateQuizExerciseFiles(importedExercise, nullsafeFiles, false);

        final var originalQuizExercise = quizExerciseRepository.findByIdElseThrow(sourceExerciseId);
        QuizExercise newQuizExercise = quizExerciseImportService.importQuizExercise(originalQuizExercise, importedExercise, files);

        return ResponseEntity.created(new URI("/api/quiz-exercises/" + newQuizExercise.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newQuizExercise.getId().toString())).body(newQuizExercise);
    }

    private void setQuizBatches(User user, QuizExercise quizExercise) {
        if (quizExercise.getQuizMode() != null) {
            Set<QuizBatch> batches = switch (quizExercise.getQuizMode()) {
                case SYNCHRONIZED -> quizBatchRepository.findAllByQuizExercise(quizExercise);
                case BATCHED -> quizBatchRepository.findAllByQuizExerciseAndCreator(quizExercise, user.getId());
                case INDIVIDUAL -> Set.of();
            };
            quizExercise.setQuizBatches(batches);
        }
    }
}
