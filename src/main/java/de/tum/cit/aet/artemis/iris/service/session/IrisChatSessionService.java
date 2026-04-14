package de.tum.cit.aet.artemis.iris.service.session;

import java.time.LocalDate;
import java.time.ZoneId;
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
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

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

    private final ExerciseRepository exerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final MessageSource messageSource;

    private final IrisChatPipelineDTOFactory irisChatPipelineDTOFactory;

    public IrisChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisChatSessionRepository irisChatSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, ObjectMapper objectMapper, ExerciseRepository exerciseRepository,
            SubmissionRepository submissionRepository, CourseRepository courseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            IrisCitationService irisCitationService, MessageSource messageSource, IrisChatPipelineDTOFactory irisChatPipelineDTOFactory) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService, Optional.of(irisCitationService));
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.messageSource = messageSource;
        this.irisChatPipelineDTOFactory = irisChatPipelineDTOFactory;
    }
    // -------------------------------------------------------------------------
    // IrisChatBasedFeatureInterface implementation
    // -------------------------------------------------------------------------

    @Override
    public void sendOverWebsocket(IrisChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void requestAndHandleResponse(IrisChatSession session) {
        doRequestAndHandleResponse(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    /**
     * Sends all messages of the session to the LLM with optional uncommitted file changes.
     * Only applicable for programming exercise sessions.
     *
     * @param session          The chat session
     * @param uncommittedFiles The uncommitted files from the client
     */
    public void requestAndHandleResponseWithUncommittedChanges(IrisChatSession session, Map<String, String> uncommittedFiles) {
        doRequestAndHandleResponse(session, Optional.empty(), Optional.empty(), Optional.empty(), uncommittedFiles);
    }

    private void doRequestAndHandleResponse(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings,
            Optional<ProgrammingSubmission> latestSubmission, Map<String, String> uncommittedFiles) {
        var chatSession = (IrisChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        var course = courseRepository.findByIdElseThrow(chatSession.getCourseId());
        var actualSettings = settings.orElseGet(() -> irisSettingsService.getSettingsForCourse(course));
        if (!actualSettings.enabled()) {
            throw new ConflictException("Iris is not enabled", "Iris", "irisDisabled");
        }

        // Validate exam exercises
        if (chatSession.getMode() == IrisChatMode.PROGRAMMING_EXERCISE_CHAT || chatSession.getMode() == IrisChatMode.TEXT_EXERCISE_CHAT) {
            var exercise = exerciseRepository.findByIdElseThrow(chatSession.getEntityId());
            if (exercise.isExamExercise()) {
                throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
            }
        }

        pyrisPipelineService.executeChatPipeline(actualSettings.variant().jsonValue(), chatSession, event, executionDto -> irisChatPipelineDTOFactory
                .buildChatDTO(chatSession.getMode(), chatSession, executionDto, actualSettings.customInstructions(), course, latestSubmission, uncommittedFiles));
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
        user.hasOptedIntoLLMUsageElseThrow();

        // Session ownership check (uniform across all contexts)
        if (!Objects.equals(session.getUserId(), user.getId())) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }

        // Role check — branching based on persisted chat mode
        switch (session.getMode()) {
            case PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT -> {
                var exercise = exerciseRepository.findByIdElseThrow(session.getEntityId());
                authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(session.getEntityId());
                authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);
            }
            case COURSE_CHAT -> {
                var course = courseRepository.findByIdElseThrow(session.getCourseId());
                authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
            }
            default -> throw new IllegalStateException("IrisChatSessionService.checkHasAccessTo does not handle chat mode " + session.getMode());
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
    // LLM token usage
    // -------------------------------------------------------------------------

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisChatSession session) {
        builder.withCourse(session.getCourseId());
        if (session.getMode() == IrisChatMode.PROGRAMMING_EXERCISE_CHAT || session.getMode() == IrisChatMode.TEXT_EXERCISE_CHAT) {
            builder.withExercise(session.getEntityId());
        }
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
        var session = findOrCreateExerciseSession(studentParticipation.getProgrammingExercise(), user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        rateLimitService.checkRateLimitElseThrow(session, user);
        log.info("Build failed for user {}", user.getName());
        try {
            CompletableFuture.runAsync(() -> doRequestAndHandleResponse(session, Optional.of(IrisEventType.BUILD_FAILED.name().toLowerCase()), Optional.of(settings),
                    Optional.of(submission), Map.of()));
        }
        catch (Exception e) {
            log.error("Error while sending build failed message to Iris for session {}", session.getId(), e);
        }
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
                var session = findOrCreateExerciseSession(studentParticipation.getProgrammingExercise(), user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
                rateLimitService.checkRateLimitElseThrow(session, user);
                try {
                    CompletableFuture.runAsync(() -> doRequestAndHandleResponse(session, Optional.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase()), Optional.of(settings),
                            Optional.of(latestSubmission), Map.of()));
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
     *
     * @param courseId the course ID
     * @param mode     the chat mode (determines how entityId is interpreted)
     * @param entityId entity ID — exerciseId for exercise modes, lectureId for LECTURE_CHAT, courseId for COURSE_CHAT
     * @param user     the user
     * @return the current (or newly created) Iris session
     */
    public IrisChatSession getCurrentSessionOrCreateIfNotExists(long courseId, IrisChatMode mode, long entityId, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);

        return switch (mode) {
            case PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT -> {
                var exercise = exerciseRepository.findByIdElseThrow(entityId);
                yield findOrCreateExerciseSession(exercise, user, mode);
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(entityId);
                yield findOrCreateLectureSession(lecture, user);
            }
            case COURSE_CHAT -> findOrCreateCourseSession(course, user);
            default -> throw new IllegalStateException("IrisChatSessionService.getCurrentSessionOrCreateIfNotExists does not handle chat mode " + mode);
        };
    }

    /**
     * Creates a new Iris chat session for the given context.
     *
     * @param courseId the course ID
     * @param mode     the chat mode (determines how entityId is interpreted)
     * @param entityId entity ID — exerciseId for exercise modes, lectureId for LECTURE_CHAT, courseId for COURSE_CHAT
     * @param user     the user
     * @return the newly created session
     */
    public IrisChatSession createSession(long courseId, IrisChatMode mode, long entityId, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);

        return switch (mode) {
            case PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT -> {
                var exercise = exerciseRepository.findByIdElseThrow(entityId);
                yield createExerciseSessionInternal(exercise, user, mode);
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(entityId);
                yield createLectureSessionInternal(lecture, user);
            }
            case COURSE_CHAT -> createCourseSessionInternal(course, user);
            default -> throw new IllegalStateException("IrisChatSessionService.createSession does not handle chat mode " + mode);
        };
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval — private helpers
    // -------------------------------------------------------------------------

    private IrisChatSession findOrCreateExerciseSession(Exercise exercise, User user, IrisChatMode mode) {
        return irisChatSessionRepository.findLatestByEntityIdAndChatModeAndUserIdWithMessages(exercise.getId(), mode, user.getId(), Pageable.ofSize(1)).stream().findFirst()
                .orElseGet(() -> createExerciseSessionInternal(exercise, user, mode));
    }

    private IrisChatSession findOrCreateCourseSession(Course course, User user) {
        var sessionOptional = irisChatSessionRepository
                .findLatestByEntityIdAndChatModeAndUserIdWithMessages(course.getId(), IrisChatMode.COURSE_CHAT, user.getId(), Pageable.ofSize(1)).stream().findFirst();
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
        return irisChatSessionRepository.findLatestByEntityIdAndChatModeAndUserIdWithMessages(lecture.getId(), IrisChatMode.LECTURE_CHAT, user.getId(), Pageable.ofSize(1)).stream()
                .findFirst().orElseGet(() -> createLectureSessionInternal(lecture, user));
    }

    private IrisChatSession createExerciseSessionInternal(Exercise exercise, User user, IrisChatMode mode) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var session = new IrisChatSession(exercise, user, mode);
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
