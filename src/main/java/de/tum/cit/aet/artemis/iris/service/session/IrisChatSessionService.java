package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.COURSE_CHAT;
import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisContextSwitchMarker;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
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
 * <p>
 * Invariant: every session is born a {@code COURSE_CHAT} via {@link #findOrCreateEmptyCourseSession} — the only
 * session creator. Exercise/lecture context is never baked in at creation; it only ever arises by switching an
 * existing session's context through {@link #applyContextChange} (a user-driven switch, or a proactive
 * build-failed / progress-stalled event). The current {@code mode}/{@code entityId} is thus a moving pointer,
 * and the CTXSWAP markers in the message history record the transitions.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisChatSessionService extends AbstractIrisChatSessionService<IrisChatSession> {

    private static final Logger log = LoggerFactory.getLogger(IrisChatSessionService.class);

    private final IrisSettingsService irisSettingsService;

    private final UserAiPreferenceService userAiPreferenceService;

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

    private final TransactionTemplate transactionTemplate;

    /**
     * Instance-wide kill switch for Artemis' own build-triggered proactive events. Deliberately kept alongside the
     * per-course setting: this one is checked before any result, course or DB access, so it can stop the whole
     * mechanism without a settings lookup. Both must be on for a trigger to fire.
     */
    private final boolean globalLegacyBuildTriggersEnabled;

    public IrisChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisChatSessionRepository irisChatSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, ObjectMapper objectMapper, ExerciseRepository exerciseRepository, SubmissionRepository submissionRepository,
            CourseRepository courseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi, IrisCitationService irisCitationService, MessageSource messageSource,
            IrisChatPipelineExecutionService chatPipelineExecutionService, PyrisJobService pyrisJobService, UserAiPreferenceService userAiPreferenceService,
            PlatformTransactionManager transactionManager, @Value("${artemis.iris.proactive.legacy-build-triggers:true}") boolean globalLegacyBuildTriggersEnabled) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService, Optional.of(irisCitationService), pyrisJobService);
        this.irisSettingsService = irisSettingsService;
        this.userAiPreferenceService = userAiPreferenceService;
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.globalLegacyBuildTriggersEnabled = globalLegacyBuildTriggersEnabled;
    }
    // -------------------------------------------------------------------------
    // IrisChatBasedFeatureInterface implementation
    // -------------------------------------------------------------------------

    @Override
    public void sendOverWebsocket(IrisChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null, null);
    }

    @Override
    public void requestAndHandleResponse(IrisChatSession session) {
        chatPipelineExecutionService.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), List.of());
    }

    /**
     * Sends all messages of the session to the LLM with optional uncommitted file changes and optional context information.
     *
     * @param session          The chat session
     * @param uncommittedFiles The uncommitted files from the client
     * @param context          Optional list of context objects providing information about what the user is viewing (not persisted)
     */
    public void requestAndHandleResponseWithAdditionalData(IrisChatSession session, Map<String, String> uncommittedFiles, List<IrisMessageContextDTO> context) {
        chatPipelineExecutionService.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), uncommittedFiles, context);
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
        // No LLM opt-in check here. IrisSessionService#checkHasAccessToIrisSession owns that gate for every
        // session type (the tutor-suggestion implementation of this method does not carry one either), and it is
        // the only caller of this method. A second, unconditional copy took the decision away from it: a caller
        // that must record an already-delivered hint's outcome without demanding a live opt-in could not do so.

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
        if (session.getMode() == PROGRAMMING_EXERCISE_CHAT || session.getMode() == IrisChatMode.TEXT_EXERCISE_CHAT) {
            builder.withExercise(session.getEntityId());
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * Handles the new result event for a programming exercise session.
     * Checks if the build failed or if the student needs intervention based on their score trajectory.
     * <p>
     * Invoked indirectly: {@link de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventService#trigger} republishes
     * the incoming {@link NewResultEvent} via {@link org.springframework.context.ApplicationEventPublisher} so
     * this service stays decoupled from the dispatch path (avoids a long deferred-eager init dependency chain).
     *
     * @param resultEvent The result event of the submission
     */
    @EventListener
    public void handleNewResultEvent(NewResultEvent resultEvent) {
        if (!globalLegacyBuildTriggersEnabled) {
            return;
        }
        var result = resultEvent.getEventObject();
        var participation = result.getSubmission().getParticipation();

        if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) || participation.getExercise().isExamExercise()) {
            return;
        }

        if (!studentParticipation.getStudent().map(student -> userAiPreferenceService.hasOptedIntoLlmUsage(student.getId())).orElse(false)) {
            return;
        }

        var programmingSubmission = (ProgrammingSubmission) result.getSubmission();
        // Loaded once and handed down, so both branches share this lookup instead of repeating it for their own
        // `enabled()` check. The per-course legacy switch belongs to the same lookup, so it is decided here too.
        var settings = irisSettingsService.getSettingsForCourse(studentParticipation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember());
        if (!settings.enabled() || !settings.legacyBuildTriggersEffective()) {
            return;
        }
        if (programmingSubmission.isBuildFailed()) {
            onBuildFailure(studentParticipation, programmingSubmission, settings);
        }
        else {
            onNewResult(studentParticipation, programmingSubmission, settings);
        }
    }

    private void onBuildFailure(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission submission, IrisCourseSettings settings) {
        var user = studentParticipation.getStudent().orElseThrow();
        var session = findExerciseSessionOrCourseFallback(studentParticipation.getProgrammingExercise(), user, PROGRAMMING_EXERCISE_CHAT);
        if (session.getMode() == COURSE_CHAT) {
            applyContextChange(session, PROGRAMMING_EXERCISE_CHAT, studentParticipation.getProgrammingExercise().getId(), user);
        }
        rateLimitService.checkRateLimitElseThrow(session, user);
        log.info("Build failed for user {}", user.getName());
        CompletableFuture.runAsync(() -> chatPipelineExecutionService.execute(session, Optional.of(IrisEventType.BUILD_FAILED.name().toLowerCase(Locale.ROOT)),
                Optional.of(settings), Optional.of(submission), Map.of(), List.of())).exceptionally(e -> {
                    log.error("Error while sending build failed message to Iris for session {}", session.getId(), e);
                    return null;
                });
    }

    private void onNewResult(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission latestSubmission, IrisCourseSettings settings) {
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
                var session = findExerciseSessionOrCourseFallback(studentParticipation.getProgrammingExercise(), user, PROGRAMMING_EXERCISE_CHAT);
                if (session.getMode() == COURSE_CHAT) {
                    applyContextChange(session, PROGRAMMING_EXERCISE_CHAT, studentParticipation.getProgrammingExercise().getId(), user);
                }
                rateLimitService.checkRateLimitElseThrow(session, user);
                CompletableFuture.runAsync(() -> chatPipelineExecutionService.execute(session, Optional.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase(Locale.ROOT)),
                        Optional.of(settings), Optional.of(latestSubmission), Map.of(), List.of())).exceptionally(e -> {
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
     * Resolved entity references for a chat context. Exactly one of {@code exercise} or {@code lecture}
     * is populated for entity-bound modes; for COURSE_CHAT both are {@code null} and the course itself
     * is the context. {@code course} is always set (the parent course for entity modes).
     */
    private record ResolvedContext(String entityName, Course course, Exercise exercise, Lecture lecture) {
    }

    /**
     * Loads the target entity for {@code (mode, entityId)}, validates the mode against the entity type,
     * checks the user's role on the parent course, and ensures Iris is enabled for that course.
     * Shared by {@link #getCurrentSessionOrCreateIfNotExists}, {@link #findOrCreateEmptySession}, and
     * {@link #applyContextChange} so the validate/authorize/enable chain stays in one place.
     */
    private ResolvedContext resolveAndAuthorize(IrisChatMode mode, long entityId, User user) {
        return switch (mode) {
            case PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT -> {
                var exercise = exerciseRepository.findByIdElseThrow(entityId);
                validateExerciseMode(exercise, mode);
                authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
                var course = exercise.getCourseViaExerciseGroupOrCourseMember();
                irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
                yield new ResolvedContext(exercise.getTitle(), course, exercise, null);
            }
            case LECTURE_CHAT -> {
                var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(entityId);
                authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);
                var course = lecture.getCourse();
                irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
                yield new ResolvedContext(lecture.getTitle(), course, null, lecture);
            }
            case COURSE_CHAT -> {
                var course = courseRepository.findByIdElseThrow(entityId);
                authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
                irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
                yield new ResolvedContext(course.getTitle(), course, null, null);
            }
            default -> throw new IllegalStateException("IrisChatSessionService.resolveAndAuthorize does not handle chat mode " + mode);
        };
    }

    /**
     * Gets or creates the current Iris chat session for the given context.
     * The course is derived from the entity (exercise/lecture) or, for COURSE_CHAT, from the entityId itself.
     *
     * @param mode     the chat mode (determines how entityId is interpreted)
     * @param entityId entity ID — exerciseId for exercise modes, lectureId for LECTURE_CHAT, courseId for COURSE_CHAT
     * @param user     the user
     * @return the current (or newly created) Iris session
     */
    public IrisChatSession getCurrentSessionOrCreateIfNotExists(IrisChatMode mode, long entityId, User user) {
        userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId());
        var resolved = resolveAndAuthorize(mode, entityId, user);
        return switch (mode) {
            case PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT -> findExerciseSessionOrCourseFallback(resolved.exercise(), user, mode);
            case LECTURE_CHAT -> findLectureSessionOrCourseFallback(resolved.lecture(), user);
            case COURSE_CHAT -> findOrCreateEmptyCourseSession(resolved.course(), user);
            default -> throw new IllegalStateException("IrisChatSessionService.getCurrentSessionOrCreateIfNotExists does not handle chat mode " + mode);
        };
    }

    /**
     * Applies a pending context (chatMode + entityId) change to an already-loaded Iris chat session.
     * <p>
     * Persists a {@link IrisMessageSender#CTXSWAP CTXSWAP} marker message into the chat history so the
     * LLM can interpret previous messages against the old context and focus subsequent replies on the
     * new context, and pushes the marker over the websocket so the client can render the divider in
     * sequence with the user message that triggered the switch. The session is <b>not</b> recreated;
     * its id and websocket subscription remain valid.
     * <p>
     * Intended to be called inline at the start of {@code POST sessions/{id}/messages} when the client
     * forwards a pending context together with the new user message — so the switch and the message
     * land in one atomic round trip.
     *
     * @param session     the chat session to update (must already be loaded with messages)
     * @param newMode     the new chat mode
     * @param newEntityId the new entity id (exerciseId / lectureId / courseId depending on mode)
     * @param user        the requesting user
     */
    public void applyContextChange(IrisChatSession session, IrisChatMode newMode, long newEntityId, User user) {
        // Deliberately NO pre-check on the caller's copy here. It looks like a cheap way to skip a no-op, but the
        // caller's copy can be stale in both directions: if it still says A while another request has moved the
        // session to B, a request to switch to A would return early and leave the session on B - silently doing
        // nothing where a real transition was asked for. The only check that decides is the one under the lock.
        //
        // Depends only on the target and the user, never on the session's current state, so it stays outside the lock.
        var resolved = resolveAndAuthorize(newMode, newEntityId, user);

        // Append the marker and update the context fields under ONE session write lock. saveMessage takes that lock
        // itself, but if it were the only holder it would release it on its own commit, and the save below would then
        // cascade-merge the session - whose messages list is the one saveMessage handed back - after a concurrent
        // append may already have committed. orphanRemoval would delete that new row. Taking the lock here keeps it
        // held until this transaction commits, so nothing can interleave between the two writes.
        IrisMessage savedMarker = transactionTemplate.execute(status -> {
            if (!(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId()) instanceof IrisChatSession locked)) {
                throw new IllegalStateException("Context can only be switched on a chat session, but session " + session.getId() + " is not one");
            }
            // Taking the lock does not refresh an entity the persistence context already manages, and a caller can
            // hand us one it loaded earlier. Without the refresh the state we decide on could be the one that was
            // read before the lock existed. Flush first so the refresh cannot discard changes a caller made but has
            // not written yet. This holds whether or not the caller runs it inside a transaction, because either way
            // the entity can reach here pre-loaded.
            irisSessionRepository.flush();
            irisSessionRepository.refresh(locked);
            // Everything describing the transition comes from the LOCKED session, not from the caller's copy. A
            // concurrent switch that took the lock first has already moved the session on, so a decision made before
            // the lock would either record a previous mode that never was, or append a second marker for a switch
            // that already happened.
            if (locked.getMode() == newMode && locked.getEntityId() == newEntityId) {
                return null;   // the other switch picked the same target; there is no transition left to record
            }
            if (resolved.course().getId() != locked.getCourseId()) {
                throw new ConflictException("New context must belong to the same course as the session", "Iris", "irisCourseMismatch");
            }

            var marker = IrisContextSwitchMarker.forSwitch(locked.getMode(), newMode, newEntityId, resolved.entityName());
            IrisMessage markerMessage = new IrisMessage();
            markerMessage.addContent(new IrisJsonMessageContent(JsonObjectMapper.get().valueToTree(marker)));
            IrisMessage saved = irisMessageService.saveMessage(markerMessage, locked, IrisMessageSender.CTXSWAP);

            locked.setMode(newMode);
            locked.setEntityId(newEntityId);
            irisChatSessionRepository.save(locked);
            return saved;
        });

        if (savedMarker == null) {
            // Lost the race to an identical switch. The session already carries the target context, so mirror it and
            // stay quiet rather than announcing a transition this call did not make.
            session.setMode(newMode);
            session.setEntityId(newEntityId);
            return;
        }

        // Mirror onto the caller's instance: callers read the mode straight after this call - resolveProactiveSession
        // gates the entire proactive flow on it - and the locked instance they never see is the one that was updated.
        session.setMode(newMode);
        session.setEntityId(newEntityId);
        sendOverWebsocket(session, savedMarker);
    }

    private void validateExerciseMode(Exercise exercise, IrisChatMode mode) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        boolean isProgramming = exercise instanceof ProgrammingExercise;
        boolean isText = exercise instanceof TextExercise;
        if (mode == PROGRAMMING_EXERCISE_CHAT && !isProgramming) {
            throw new ConflictException("Exercise type does not match PROGRAMMING_EXERCISE_CHAT mode", "Iris", "irisExerciseModeMismatch");
        }
        if (mode == IrisChatMode.TEXT_EXERCISE_CHAT && !isText) {
            throw new ConflictException("Exercise type does not match TEXT_EXERCISE_CHAT mode", "Iris", "irisExerciseModeMismatch");
        }
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval
    // -------------------------------------------------------------------------

    /**
     * Public entry point for the "New Chat" button: authorizes the course context once (LLM opt-in, student
     * role, Iris enabled) and then delegates to {@link #findOrCreateEmptyCourseSession}. Callers that have
     * already authorized the course — e.g. {@link #getCurrentSessionOrCreateIfNotExists} and the
     * {@code find…OrCourseFallback} helpers — invoke that private core directly to avoid a redundant
     * authorize/entity-load round trip.
     *
     * @param courseId the id of the course to create or retrieve the empty session for
     * @param user     the user requesting the session
     * @return the existing reusable empty course chat session for today, or a newly created one
     */
    public IrisChatSession findOrCreateEmptySession(long courseId, User user) {
        userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId());
        var course = resolveAndAuthorize(IrisChatMode.COURSE_CHAT, courseId, user).course();
        return findOrCreateEmptyCourseSession(course, user);
    }

    /**
     * Non-authorizing core that finds the user's reusable empty course chat session for today, or creates a
     * fresh one. The single place a chat session is created.
     * <p>
     * Assumes the caller has already authorized {@code course} for {@code user} (LLM opt-in + student role +
     * Iris enabled). No access check is repeated here: the lookup is scoped to {@code user.getId()}, so the
     * returned session always belongs to the user.
     */
    private IrisChatSession findOrCreateEmptyCourseSession(Course course, User user) {
        var sessionOptional = irisChatSessionRepository
                .findLatestByEntityIdAndChatModeAndUserIdWithMessages(course.getId(), IrisChatMode.COURSE_CHAT, user.getId(), Pageable.ofSize(1)).stream().findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            // Reuse today's course session only while it is still empty; once it has messages (or is from
            // an earlier day) a new one is created, so each fresh entry starts on a clean course chat.
            if (session.getCreationDate().withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now(ZoneId.systemDefault()))
                    && session.getMessages().isEmpty()) {
                return session;
            }
        }
        var session = new IrisChatSession(course, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }

    private IrisChatSession findExerciseSessionOrCourseFallback(Exercise exercise, User user, IrisChatMode mode) {
        return irisChatSessionRepository.findLatestByEntityIdAndChatModeAndUserIdWithMessages(exercise.getId(), mode, user.getId(), Pageable.ofSize(1)).stream().findFirst()
                .orElseGet(() -> findOrCreateEmptyCourseSession(exercise.getCourseViaExerciseGroupOrCourseMember(), user));
    }

    private IrisChatSession findLectureSessionOrCourseFallback(Lecture lecture, User user) {
        return irisChatSessionRepository.findLatestByEntityIdAndChatModeAndUserIdWithMessages(lecture.getId(), IrisChatMode.LECTURE_CHAT, user.getId(), Pageable.ofSize(1)).stream()
                .findFirst().orElseGet(() -> findOrCreateEmptyCourseSession(lecture.getCourse(), user));
    }
}
