package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.atlas.api.LearningMetricsApi;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.ChatContext;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisEventDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisExerciseWithStudentSubmissionsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureUnitDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.text.api.TextApi;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Unified service for all Iris chat sessions (programming exercise, text exercise, course, lecture).
 * Replaces IrisExerciseChatSessionService, IrisTextExerciseChatSessionService,
 * IrisCourseChatSessionService, and IrisLectureChatSessionService.
 * <p>
 * Dispatches to the appropriate pipeline based on which context fields are set on the session:
 * - exerciseId != null → exercise chat (programming or text, determined by repository lookup)
 * - lectureId != null → lecture chat
 * - else → course chat
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisChatSessionService extends AbstractIrisChatSessionService<IrisChatSession> {

    private static final Logger log = LoggerFactory.getLogger(IrisChatSessionService.class);

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExerciseRepository exerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final PyrisDTOService pyrisDTOService;

    private final Optional<LearningMetricsApi> learningMetricsApi;

    private final IrisMessageService irisMessageService;

    private final Optional<IrisCitationService> irisCitationService;

    private final MessageSource messageSource;

    public IrisChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisChatSessionRepository irisChatSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, ProgrammingExerciseRepository programmingExerciseRepository,
            ObjectMapper objectMapper, ExerciseRepository exerciseRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, UserRepository userRepository, CourseRepository courseRepository,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<TextRepositoryApi> textRepositoryApi, IrisCitationService irisCitationService,
            MessageSource messageSource, PyrisDTOService pyrisDTOService, Optional<LearningMetricsApi> learningMetricsApi) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService, Optional.of(irisCitationService));
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.textRepositoryApi = textRepositoryApi;
        this.irisMessageService = irisMessageService;
        this.irisCitationService = Optional.of(irisCitationService);
        this.messageSource = messageSource;
        this.pyrisDTOService = pyrisDTOService;
        this.learningMetricsApi = learningMetricsApi;
    }

    // -------------------------------------------------------------------------
    // Context helpers
    // -------------------------------------------------------------------------

    private ChatContext determineChatContext(IrisChatSession session) {
        if (session.getExerciseId() != null) {
            return programmingExerciseRepository.existsById(session.getExerciseId()) ? ChatContext.EXERCISE : ChatContext.TEXT_EXERCISE;
        }
        return session.getLectureId() != null ? ChatContext.LECTURE : ChatContext.COURSE;
    }

    // -------------------------------------------------------------------------
    // IrisChatBasedFeatureInterface implementation TODO: Überarbeiten
    // -------------------------------------------------------------------------

    @Override
    public void sendOverWebsocket(IrisChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void requestAndHandleResponse(IrisChatSession session) {
        requestAndHandleResponse(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    /**
     * Sends all messages of the session to the LLM with optional uncommitted file changes.
     * Only applicable for programming exercise sessions.
     *
     * @param session          The chat session
     * @param uncommittedFiles The uncommitted files from the client
     */
    public void requestAndHandleResponseWithUncommittedChanges(IrisChatSession session, Map<String, String> uncommittedFiles) {
        requestAndHandleResponse(session, Optional.empty(), Optional.empty(), Optional.empty(), uncommittedFiles);
    }

    /**
     * Sends all messages of the session to the LLM with optional event, settings, and submission.
     * Only applicable for programming exercise sessions.
     *
     * @param session          The chat session
     * @param event            The event to trigger on the Pyris side
     * @param settings         Optional pre-loaded settings; fetched if absent
     * @param latestSubmission Optional pre-loaded submission; fetched if absent
     */
    public void requestAndHandleResponse(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings, Optional<ProgrammingSubmission> latestSubmission) {
        requestAndHandleResponse(session, event, settings, latestSubmission, Map.of());
    }

    private void requestAndHandleResponse(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings, Optional<ProgrammingSubmission> latestSubmission,
            Map<String, String> uncommittedFiles) {
        requestAndHandleResponse(session, event, settings, latestSubmission, uncommittedFiles, null);
    }

    private void requestAndHandleResponse(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings, Optional<ProgrammingSubmission> latestSubmission,
            Map<String, String> uncommittedFiles, Object eventObject) {
        var chatSession = (IrisChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        var course = courseRepository.findByIdElseThrow(chatSession.getCourseId());
        var actualSettings = settings.orElseGet(() -> irisSettingsService.getSettingsForCourse(course));
        if (!actualSettings.enabled()) {
            throw new ConflictException("Iris is not enabled", "Iris", "irisDisabled");
        }

        var context = determineChatContext(chatSession);

        // Validate exam exercises
        if (chatSession.getExerciseId() != null) {
            var exercise = exerciseRepository.findByIdElseThrow(chatSession.getExerciseId());
            if (exercise.isExamExercise()) {
                throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
            }
        }

        pyrisPipelineService.executeChatPipeline(actualSettings.variant().jsonValue(), chatSession, event,
                executionDto -> buildChatDTO(context, chatSession, executionDto, actualSettings.customInstructions(), course, latestSubmission, uncommittedFiles, eventObject));
    }

    // -------------------------------------------------------------------------
    // IrisRateLimitedFeatureInterface implementation
    // -------------------------------------------------------------------------

    @Override
    public void checkRateLimit(User user, IrisChatSession session) {
        rateLimitService.checkRateLimitElseThrow(session, user);
    }

    // -------------------------------------------------------------------------
    // IrisSubFeatureInterface implementation
    // -------------------------------------------------------------------------

    /**
     * Checks if the user has access to the given Iris chat session.
     * Access rules differ by context (exercise, lecture, or course).
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisChatSession session) {
        // LLM opt-in required for lecture and course chats, but not for exercise chats
        if (session.getExerciseId() == null) {
            user.hasOptedIntoLLMUsageElseThrow();
        }

        // Session ownership check (uniform across all contexts)
        if (!Objects.equals(session.getUserId(), user.getId())) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }

        // Role check — branching required because authCheckService has separate methods per scope
        if (session.getExerciseId() != null) {
            var exercise = exerciseRepository.findByIdElseThrow(session.getExerciseId());
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        }
        else if (session.getLectureId() != null) {
            var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(session.getLectureId());
            authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);
        }
        else {
            var course = courseRepository.findByIdElseThrow(session.getCourseId());
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        }
    }

    /**
     * Checks if Iris is enabled for the course associated with the session.
     *
     * @param session The session to check
     */
    @Override
    public void checkIrisEnabledFor(IrisChatSession session) {
        var course = courseRepository.findByIdElseThrow(session.getCourseId());
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
    }

    // -------------------------------------------------------------------------
    // LLM token usage TODO: Vereinfachen ?
    // -------------------------------------------------------------------------

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisChatSession session) {
        builder.withCourse(session.getCourseId());
        if (session.getExerciseId() != null) {
            builder.withExercise(session.getExerciseId());
        }
    }

    // -------------------------------------------------------------------------
    // Pipeline DTO construction
    // -------------------------------------------------------------------------

    /**
     * Builds the {@link PyrisChatPipelineExecutionDTO} for the given chat context.
     * Uses a single switch to collect context-specific data and populate the appropriate fields.
     */
    private PyrisChatPipelineExecutionDTO buildChatDTO(ChatContext context, IrisChatSession session, PyrisPipelineExecutionDTO executionDto, String customInstructions,
            Course course, Optional<ProgrammingSubmission> latestSubmission, Map<String, String> uncommittedFiles, Object eventObject) {
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var messages = pyrisDTOService.toPyrisMessageDTOList(session.getMessages());

        // Context-specific fields (default: null)
        PyrisCourseDTO courseDto = null;
        Object exercise = null;
        PyrisLectureDTO lectureDto = null;
        PyrisSubmissionDTO progSubmission = null;
        String textSubmission = null;
        StudentMetricsDTO metrics = null;
        PyrisEventDTO<?> eventPayload = null;

        switch (context) {
            case EXERCISE -> {
                var progExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getExerciseId());
                exercise = pyrisDTOService.toPyrisProgrammingExerciseDTO(progExercise);
                var actualSubmission = latestSubmission.or(() -> getLatestSubmissionIfExists(progExercise, user));
                progSubmission = actualSubmission.map(s -> pyrisDTOService.toPyrisSubmissionDTO(s, uncommittedFiles)).orElse(null);
                courseDto = new PyrisCourseDTO(course);
            }
            case TEXT_EXERCISE -> {
                var textExercise = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class)).findByIdElseThrow(session.getExerciseId());
                exercise = PyrisTextExerciseDTO.of(textExercise);
                var participation = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(textExercise.getId(), user.getLogin());
                var latest = participation.flatMap(p -> p.getSubmissions().stream().max(Comparator.comparingLong(Submission::getId))).orElse(null);
                textSubmission = latest instanceof TextSubmission ts ? ts.getText() : null;
            }
            case LECTURE -> {
                var api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
                var lecture = api.findByIdWithLectureUnitsElseThrow(session.getLectureId());
                Long courseId = course.getId();
                List<PyrisLectureUnitDTO> lectureUnits = lecture.getLectureUnits() == null ? List.of() : lecture.getLectureUnits().stream().map(unit -> {
                    Integer attachmentVersion = null;
                    if (unit instanceof AttachmentVideoUnit attachmentUnit && attachmentUnit.getAttachment() != null && attachmentUnit.getAttachment().getVersion() != null) {
                        attachmentVersion = attachmentUnit.getAttachment().getVersion();
                    }
                    return new PyrisLectureUnitDTO(unit.getId(), courseId, lecture.getId(), toInstant(unit.getReleaseDate()), unit.getName(), attachmentVersion);
                }).toList();
                lectureDto = new PyrisLectureDTO(lecture.getId(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(), lecture.getEndDate(), lectureUnits);
                courseDto = new PyrisCourseDTO(course);
            }
            case COURSE -> {
                var fullCourse = pyrisPipelineService.loadCourseWithParticipationOfStudent(course.getId(), session.getUserId());
                courseDto = PyrisCourseDTO.of(fullCourse);
                metrics = learningMetricsApi.map(api -> api.getStudentCourseMetrics(session.getUserId(), course.getId())).orElse(null);
                eventPayload = switch (eventObject) {
                    case Exercise ex -> pyrisPipelineService.generateEventPayloadFromObjectType(PyrisExerciseWithStudentSubmissionsDTO.class, ex);
                    case null -> null;
                    default -> throw new UnsupportedOperationException("Unsupported Pyris event payload type: " + eventObject);
                };
            }
        }

        return new PyrisChatPipelineExecutionDTO(context, session.getTitle(), messages, new PyrisUserDTO(user), executionDto.settings(), executionDto.initialStages(),
                customInstructions, courseDto, exercise, lectureDto, null, progSubmission, textSubmission, metrics, eventPayload);
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * Handles the new result event for a programming exercise session.
     * Checks if the build failed or if the student needs intervention based on their score trajectory.
     *
     * @param resultEvent The result event of the submission
     */
    public void handleNewResultEvent(NewResultEvent resultEvent) {
        var result = resultEvent.getEventObject();
        var participation = result.getSubmission().getParticipation();

        if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) || participation.getExercise().isExamExercise()) {
            return;
        }

        if (!studentParticipation.getStudent().map(User::hasOptedIntoLLMUsage).orElse(false)) {
            return;
        }

        var programmingSubmission = (ProgrammingSubmission) result.getSubmission();
        if (programmingSubmission.isBuildFailed()) {
            onBuildFailure(studentParticipation, programmingSubmission);
        }
        else {
            onNewResult(studentParticipation, programmingSubmission);
        }
    }

    private void onBuildFailure(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission submission) {
        var settings = irisSettingsService.getSettingsForCourse(studentParticipation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember());
        if (!settings.enabled()) {
            return;
        }

        var user = studentParticipation.getStudent().orElseThrow();
        var session = findOrCreateExerciseSession(studentParticipation.getProgrammingExercise(), user);
        rateLimitService.checkRateLimitElseThrow(session, user);
        log.info("Build failed for user {}", user.getName());
        CompletableFuture
                .runAsync(() -> requestAndHandleResponse(session, Optional.of(IrisEventType.BUILD_FAILED.name().toLowerCase()), Optional.of(settings), Optional.of(submission)));
    }

    private void onNewResult(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission latestSubmission) {
        var settings = irisSettingsService.getSettingsForCourse(studentParticipation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember());
        if (!settings.enabled()) {
            return;
        }

        // TODO: Reduce this call to the last 5 submissions or sth
        var recentSubmissions = submissionRepository.findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(studentParticipation.getId());

        double successThreshold = 100.0; // TODO: Retrieve configuration from Iris settings

        // Check if the user has already successfully submitted before
        var successfulSubmission = recentSubmissions.stream()
                .anyMatch(submission -> submission.getLatestResult() != null && submission.getLatestResult().getScore() == successThreshold);
        if (!successfulSubmission && recentSubmissions.size() >= 3) {
            var listOfScores = recentSubmissions.stream().map(Submission::getLatestResult).filter(Objects::nonNull).map(Result::getScore).toList();

            // Check if the student needs intervention based on their recent score trajectory
            var needsIntervention = needsIntervention(listOfScores);
            if (needsIntervention) {
                log.info("Scores in the last 3 submissions did not improve for user {}", studentParticipation.getParticipant().getName());
                var user = studentParticipation.getStudent().orElseThrow();
                var session = findOrCreateExerciseSession(studentParticipation.getProgrammingExercise(), user);
                rateLimitService.checkRateLimitElseThrow(session, user);
                try {
                    CompletableFuture.runAsync(() -> requestAndHandleResponse(session, Optional.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase()), Optional.of(settings),
                            Optional.of(latestSubmission)));
                }
                catch (Exception e) {
                    log.error("Error while sending progress stalled message to Iris for user {}", studentParticipation.getParticipant().getName(), e);
                }
            }
        }
        else {
            log.info("Submission was not successful for user {}", studentParticipation.getParticipant().getName());
            if (successfulSubmission) {
                log.info("User {} has already successfully submitted before, so we do not inform Iris about the submission failure",
                        studentParticipation.getParticipant().getName());
            }
        }
    }

    private boolean hasOverallImprovement(List<Double> scores, int i, int j) {
        if (i >= j || i < 0 || j >= scores.size()) {
            throw new IllegalArgumentException("Invalid interval");
        }
        return scores.get(j) > scores.get(i) && IntStream.range(i, j).allMatch(index -> scores.get(index) <= scores.get(index + 1));
    }

    private boolean needsIntervention(List<Double> scores) {
        int intervalSize = 3; // TODO: Retrieve configuration from Iris settings
        if (scores.size() < intervalSize) {
            return false; // Not enough data to make a decision
        }
        int lastIndex = scores.size() - 1;
        int startIndex = lastIndex - intervalSize + 1;
        return !hasOverallImprovement(scores, startIndex, lastIndex);
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval — unified public API
    // -------------------------------------------------------------------------

    /**
     * Gets or creates the current Iris chat session for the given context.
     * Exactly one of exerciseId or lectureId should be set, or both null for a course chat.
     *
     * @param courseId   the course ID
     * @param exerciseId optional exercise ID (null for course/lecture chats)
     * @param lectureId  optional lecture ID (null for course/exercise chats)
     * @param user       the user
     * @return the current (or newly created) Iris session
     */
    public IrisChatSession getCurrentSessionOrCreateIfNotExists(long courseId, Long exerciseId, Long lectureId, User user) {
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);

        if (exerciseId != null) {
            var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            return findOrCreateExerciseSession(exercise, user);
        }
        if (lectureId != null) {
            var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(lectureId);
            return findOrCreateLectureSession(lecture, user);
        }
        return findOrCreateCourseSession(course, user);
    }

    /**
     * Creates a new Iris chat session for the given context.
     *
     * @param courseId   the course ID
     * @param exerciseId optional exercise ID
     * @param lectureId  optional lecture ID
     * @param user       the user
     * @return the newly created session
     */
    public IrisChatSession createSession(long courseId, Long exerciseId, Long lectureId, User user) {
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);

        if (exerciseId != null) {
            var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            return createExerciseSessionInternal(exercise, user);
        }
        if (lectureId != null) {
            var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(lectureId);
            return createLectureSessionInternal(lecture, user);
        }
        return createCourseSessionInternal(course, user);
    }

    /**
     * Gets all sessions for the given context.
     *
     * @param courseId   the course ID
     * @param exerciseId optional exercise ID
     * @param lectureId  optional lecture ID
     * @param user       the user
     * @return list of sessions
     */
    public List<IrisChatSession> getAllSessions(long courseId, Long exerciseId, Long lectureId, User user) {
        if (exerciseId != null) {
            return irisChatSessionRepository.findByExerciseIdAndUserIdOrderByCreationDateDesc(exerciseId, user.getId(), Pageable.unpaged());
        }
        if (lectureId != null) {
            return irisChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(lectureId, user.getId(), Pageable.unpaged());
        }
        return irisChatSessionRepository.findByCourseIdAndExerciseIdIsNullAndLectureIdIsNullAndUserIdOrderByCreationDateDesc(courseId, user.getId(), Pageable.unpaged());
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval — private helpers
    // -------------------------------------------------------------------------

    private IrisChatSession findOrCreateExerciseSession(Exercise exercise, User user) {
        return irisChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst()
                .orElseGet(() -> createExerciseSessionInternal(exercise, user));
    }

    private IrisChatSession findOrCreateCourseSession(Course course, User user) {
        var sessionOptional = irisChatSessionRepository.findLatestCourseChatSessionsByUserIdWithMessages(course.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            // Course sessions are reused if created today; otherwise a new one is created
            if (session.getCreationDate().withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now(ZoneId.systemDefault()))) {
                checkHasAccessTo(user, session);
                return session;
            }
        }
        return createCourseSessionInternal(course, user);
    }

    private IrisChatSession findOrCreateLectureSession(Lecture lecture, User user) {
        return irisChatSessionRepository.findLatestByLectureIdAndUserIdWithMessages(lecture.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst()
                .orElseGet(() -> createLectureSessionInternal(lecture, user));
    }

    private IrisChatSession createExerciseSessionInternal(Exercise exercise, User user) {
        if (exercise.isExamExercise()) {
            throw new IrisUnsupportedExerciseTypeException("Iris is not supported for exam exercises");
        }
        var session = new IrisChatSession(exercise, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }

    private IrisChatSession createCourseSessionInternal(Course course, User user) {
        var session = new IrisChatSession(course, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }

    private IrisChatSession createLectureSessionInternal(Lecture lecture, User user) {
        var session = new IrisChatSession(lecture, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }
}
