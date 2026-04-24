package de.tum.cit.aet.artemis.iris.service.session;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unified service for all Iris chat sessions (programming exercise, text exercise, course, lecture).
 * Replaces IrisExerciseChatSessionService, IrisTextExerciseChatSessionService,
 * IrisCourseChatSessionService, and IrisLectureChatSessionService.
 * <p>
 * Dispatches to the appropriate behavior based on the {@link IrisChatMode} persisted on the session:
 * {@code PROGRAMMING_EXERCISE_CHAT}, {@code TEXT_EXERCISE_CHAT}, {@code LECTURE_CHAT}, {@code COURSE_CHAT}.
 * The session's {@code entityId} is interpreted relative to the mode (exercise, lecture, or course id).
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisChatSessionService extends AbstractIrisChatSessionService<IrisChatSession> {

    private static final Logger log = LoggerFactory.getLogger(IrisChatSessionService.class);

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final ExerciseRepository exerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final MessageSource messageSource;

    private final IrisChatPipelineExecutionService chatPipelineExecutionService;

    public IrisChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisChatSessionRepository irisChatSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, ObjectMapper objectMapper, ExerciseRepository exerciseRepository, SubmissionRepository submissionRepository,
            CourseRepository courseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi, IrisCitationService irisCitationService, MessageSource messageSource,
            IrisChatPipelineExecutionService chatPipelineExecutionService) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService, Optional.of(irisCitationService));
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.rateLimitService = rateLimitService;
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.messageSource = messageSource;
        this.chatPipelineExecutionService = chatPipelineExecutionService;
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
        chatPipelineExecutionService.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    /**
     * Sends all messages of the session to the LLM with optional uncommitted file changes.
     * Only applicable for programming exercise sessions.
     *
     * @param session          The chat session
     * @param uncommittedFiles The uncommitted files from the client
     */
    public void requestAndHandleResponseWithUncommittedChanges(IrisChatSession session, Map<String, String> uncommittedFiles) {
        chatPipelineExecutionService.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), uncommittedFiles);
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
        CompletableFuture.runAsync(() -> chatPipelineExecutionService.execute(session, Optional.of(IrisEventType.BUILD_FAILED.name().toLowerCase()), Optional.of(settings),
                Optional.of(submission), Map.of())).exceptionally(e -> {
                    log.error("Error while sending build failed message to Iris for session {}", session.getId(), e);
                    return null;
                });
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
                CompletableFuture.runAsync(() -> chatPipelineExecutionService.execute(session, Optional.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase()),
                        Optional.of(settings), Optional.of(latestSubmission), Map.of())).exceptionally(e -> {
                            log.error("Error while sending progress stalled message to Iris for user {}", studentParticipation.getParticipant().getName(), e);
                            return null;
                        });
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
                validateExerciseMatchesCourseAndMode(exercise, courseId, mode);
                yield findOrCreateExerciseSession(exercise, user, mode);
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(entityId);
                validateLectureBelongsToCourse(lecture, courseId);
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
                validateExerciseMatchesCourseAndMode(exercise, courseId, mode);
                yield createExerciseSessionInternal(exercise, user, mode);
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(entityId);
                validateLectureBelongsToCourse(lecture, courseId);
                yield createLectureSessionInternal(lecture, user);
            }
            case COURSE_CHAT -> createCourseSessionInternal(course, user);
            default -> throw new IllegalStateException("IrisChatSessionService.createSession does not handle chat mode " + mode);
        };
    }

    /**
     * Updates the context (chatMode + entityId) of an existing Iris chat session in place.
     * <p>
     * Persists a {@link IrisMessageSender#SYSTEM SYSTEM} marker message into the chat history
     * so the LLM can interpret previous messages against the old context and focus subsequent
     * replies on the new context. The session is <b>not</b> recreated; its id and websocket
     * subscription remain valid.
     * <p>
     * A context switch across course boundaries is rejected — users should create a new session
     * in the target course instead.
     *
     * @param sessionId   the id of the session to update
     * @param courseId    the course id the session is expected to belong to (from the URL path)
     * @param newMode     the new chat mode
     * @param newEntityId the new entity id (exerciseId / lectureId / courseId depending on mode)
     * @param user        the requesting user
     * @return the updated session (with messages, including the new marker)
     */
    public IrisChatSession updateSessionContext(long sessionId, long courseId, IrisChatMode newMode, long newEntityId, User user) {
        user.hasOptedIntoLLMUsageElseThrow();

        var session = (IrisChatSession) irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);

        if (!Objects.equals(session.getUserId(), user.getId())) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
        if (session.getCourseId() != courseId) {
            throw new ConflictException("Context switch across courses is not supported; create a new session instead.", "Iris", "irisContextSwitchCrossCourse");
        }

        // Idempotent: nothing to do if context is unchanged
        if (session.getMode() == newMode && session.getEntityId() != null && session.getEntityId() == newEntityId) {
            return session;
        }

        // Validate + authorize the new context (reuses existing validators and role checks)
        String newEntityName = switch (newMode) {
            case PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT -> {
                var exercise = exerciseRepository.findByIdElseThrow(newEntityId);
                validateExerciseMatchesCourseAndMode(exercise, courseId, newMode);
                if (exercise.isExamExercise()) {
                    throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
                }
                authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
                yield exercise.getTitle();
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(newEntityId);
                validateLectureBelongsToCourse(lecture, courseId);
                authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);
                yield lecture.getTitle();
            }
            case COURSE_CHAT -> {
                if (newEntityId != courseId) {
                    throw new ConflictException("For COURSE_CHAT, entityId must equal courseId", "Iris", "irisCourseEntityMismatch");
                }
                var course = courseRepository.findByIdElseThrow(courseId);
                authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
                yield course.getTitle();
            }
            default -> throw new IllegalStateException("IrisChatSessionService.updateSessionContext does not handle chat mode " + newMode);
        };

        session.setMode(newMode);
        session.setEntityId(newEntityId);
        irisChatSessionRepository.save(session);

        String langKey = user.getLangKey();
        Locale locale = langKey == null || langKey.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(langKey);
        Object[] args = { newEntityName };
        String markerContent = messageSource.getMessage("iris.chat.session.contextSwitch.marker", args, "Context changed to " + newEntityName, locale);
        IrisMessage markerMessage = new IrisMessage();
        markerMessage.addContent(new IrisTextMessageContent(markerContent));
        irisMessageService.saveMessage(markerMessage, session, IrisMessageSender.SYSTEM);

        // Re-fetch with messages to return the up-to-date history (including the marker)
        return (IrisChatSession) irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
    }

    private void validateExerciseMatchesCourseAndMode(Exercise exercise, long courseId, IrisChatMode mode) {
        var exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (exerciseCourse == null || !Objects.equals(exerciseCourse.getId(), courseId)) {
            throw new ConflictException("Exercise does not belong to the specified course", "Iris", "irisExerciseCourseMismatch");
        }
        boolean isProgramming = exercise instanceof ProgrammingExercise;
        boolean isText = exercise instanceof TextExercise;
        if (mode == IrisChatMode.PROGRAMMING_EXERCISE_CHAT && !isProgramming) {
            throw new ConflictException("Exercise type does not match PROGRAMMING_EXERCISE_CHAT mode", "Iris", "irisExerciseModeMismatch");
        }
        if (mode == IrisChatMode.TEXT_EXERCISE_CHAT && !isText) {
            throw new ConflictException("Exercise type does not match TEXT_EXERCISE_CHAT mode", "Iris", "irisExerciseModeMismatch");
        }
    }

    private void validateLectureBelongsToCourse(Lecture lecture, long courseId) {
        if (lecture.getCourse() == null || !Objects.equals(lecture.getCourse().getId(), courseId)) {
            throw new ConflictException("Lecture does not belong to the specified course", "Iris", "irisLectureCourseMismatch");
        }
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
