package de.tum.cit.aet.artemis.iris.service.session;

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
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
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

    private final PyrisJobService pyrisJobService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExerciseRepository exerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final IrisMessageService irisMessageService;

    private final Optional<IrisCitationService> irisCitationService;

    private final MessageSource messageSource;

    public IrisChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisChatSessionRepository irisChatSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService,
            ProgrammingExerciseRepository programmingExerciseRepository, ObjectMapper objectMapper, ExerciseRepository exerciseRepository,
            SubmissionRepository submissionRepository, StudentParticipationRepository studentParticipationRepository, UserRepository userRepository,
            CourseRepository courseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<TextRepositoryApi> textRepositoryApi,
            IrisCitationService irisCitationService, MessageSource messageSource) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService, Optional.of(irisCitationService));
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
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
    }

    // -------------------------------------------------------------------------
    // Context helpers
    // -------------------------------------------------------------------------

    private boolean isExerciseSession(IrisChatSession session) {
        return session.getExerciseId() != null;
    }

    private boolean isLectureSession(IrisChatSession session) {
        return session.getLectureId() != null;
    }

    private boolean isProgrammingExercise(IrisChatSession session) {
        return programmingExerciseRepository.existsById(session.getExerciseId());
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
        if (isExerciseSession(session)) {
            if (isProgrammingExercise(session)) {
                executeProgrammingExercisePipeline(session, event, settings, latestSubmission, uncommittedFiles);
            }
            else {
                executeTextExercisePipeline(session);
            }
        }
        else if (isLectureSession(session)) {
            executeLecturePipeline(session);
        }
        else {
            executeCoursePipeline(session, null);
        }
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
        if (isExerciseSession(session)) {
            // TODO: überprüfen, ob exercise so gefunden werden kann
            var exercise = exerciseRepository.findByIdElseThrow(session.getExerciseId());
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
            if (!Objects.equals(session.getUserId(), user.getId())) {
                throw new AccessForbiddenException("Iris Session", session.getId());
            }
        }
        else if (isLectureSession(session)) {
            user.hasOptedIntoLLMUsageElseThrow();
            if (session.getUserId() != user.getId()) {
                throw new AccessForbiddenException("Iris Lecture chat Session", session.getId());
            }
            var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(session.getLectureId());
            authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);
        }
        else {
            user.hasOptedIntoLLMUsageElseThrow();
            var course = courseRepository.findByIdElseThrow(session.getCourseId());
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
            if (!Objects.equals(session.getUserId(), user.getId())) {
                throw new AccessForbiddenException("Iris Session", session.getId());
            }
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
    // Pipeline execution
    // -------------------------------------------------------------------------

    private void executeProgrammingExercisePipeline(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings,
            Optional<ProgrammingSubmission> latestSubmission, Map<String, String> uncommittedFiles) {
        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getExerciseId());
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }

        var actualSettings = settings.orElseGet(() -> {
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();
            return irisSettingsService.getSettingsForCourse(course);
        });
        if (!actualSettings.enabled()) {
            throw new ConflictException("Iris is not enabled for this exercise", "Iris", "irisDisabled");
        }

        var actualUser = latestSubmission.flatMap(s -> ((ProgrammingExerciseStudentParticipation) s.getParticipation()).getStudent())
                .orElseGet(() -> userRepository.findByIdElseThrow(session.getUserId()));
        var actualLatestSubmission = latestSubmission.or(() -> getLatestSubmissionIfExists(exercise, actualUser));

        var chatSession = (IrisChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        pyrisPipelineService.executeExerciseChatPipeline(actualSettings.variant().jsonValue(), actualSettings.customInstructions(), actualLatestSubmission, exercise, chatSession,
                event, uncommittedFiles);
    }

    private void executeTextExercisePipeline(IrisChatSession session) {
        var chatSession = (IrisChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        var exercise = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class)).findByIdElseThrow(session.getExerciseId());
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this exercise", "Iris", "irisDisabled");
        }
        var user = userRepository.findByIdElseThrow(chatSession.getUserId());
        var participation = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        var latestSubmission = participation.flatMap(p -> p.getSubmissions().stream().max(Comparator.comparingLong(Submission::getId))).orElse(null);
        String latestSubmissionText = latestSubmission instanceof TextSubmission textSubmission ? textSubmission.getText() : null;
        var chatHistory = chatSession.getMessages().stream().map(PyrisMessageDTO::of).toList();
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "text-exercise-chat",
                user.getSelectedLLMUsage(),
                settings.variant().jsonValue(),
                Optional.empty(),
                pyrisJobService.createTokenForJob(token -> new TextExerciseChatJob(token, course.getId(), exercise.getId(), chatSession.getId())),
                dto -> new PyrisTextExerciseChatPipelineExecutionDTO(PyrisTextExerciseDTO.of(exercise), chatSession.getTitle(), chatHistory, new PyrisUserDTO(user),
                        latestSubmissionText, dto.settings(), dto.initialStages(), settings.customInstructions()),
                stages -> irisChatWebsocketService.sendMessage(chatSession, null, stages));
        // @formatter:on
    }

    private void executeLecturePipeline(IrisChatSession session) {
        var api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        var chatSession = (IrisChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        var lecture = api.findByIdWithLectureUnitsElseThrow(chatSession.getLectureId());
        var course = lecture.getCourse();
        if (course == null) {
            throw new ConflictException("Lecture does not belong to a course", "Iris", "lectureNoCourse");
        }
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this lecture", "Iris", "irisDisabled");
        }
        pyrisPipelineService.executeLectureChatPipeline(settings.variant().jsonValue(), settings.customInstructions(), chatSession, lecture);
    }

    private void executeCoursePipeline(IrisChatSession session, Object eventObject) {
        var chatSession = (IrisChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        var course = courseRepository.findByIdElseThrow(chatSession.getCourseId());
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this course", "Iris", "irisDisabled");
        }
        pyrisPipelineService.executeCourseChatPipeline(settings.variant().jsonValue(), settings.customInstructions(), chatSession, eventObject);
    }

    // -------------------------------------------------------------------------
    // Text exercise status update
    // -------------------------------------------------------------------------

    /**
     * Handles the status update of a text exercise chat job.
     *
     * @param job          The job that is updated
     * @param statusUpdate The status update
     * @return The same job that was passed in
     */
    public TextExerciseChatJob handleStatusUpdate(TextExerciseChatJob job, PyrisTextExerciseChatStatusUpdateDTO statusUpdate) {
        var session = (IrisChatSession) irisSessionRepository.findByIdElseThrow(job.sessionId());
        String sessionTitle = AbstractIrisChatSessionService.setSessionTitle(session, statusUpdate.sessionTitle(), irisSessionRepository);
        if (statusUpdate.result() != null) {
            var message = session.newMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            var citationInfo = irisCitationService.map(service -> service.resolveCitationInfo(statusUpdate.result())).orElse(List.of());
            IrisMessage savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages(), sessionTitle, citationInfo);
        }
        else {
            irisChatWebsocketService.sendMessage(session, null, statusUpdate.stages(), sessionTitle, null);
        }
        return job;
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
        var session = getCurrentSessionOrCreateIfNotExistsForExercise(studentParticipation.getProgrammingExercise(), user);
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
                var session = getCurrentSessionOrCreateIfNotExistsForExercise(studentParticipation.getProgrammingExercise(), user);
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

    /**
     * Handles the CompetencyJolSetEvent for a course chat session.
     *
     * @param competencyJolSetEvent The event containing the CompetencyJol
     */
    public void handleCompetencyJolSetEvent(CompetencyJolSetEvent competencyJolSetEvent) {
        var competencyJol = competencyJolSetEvent.getEventObject();
        var course = competencyJol.getCompetency().getCourse();
        var user = competencyJol.getUser();

        if (!user.hasOptedIntoLLMUsage()) {
            return;
        }

        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            return;
        }

        var session = getCurrentSessionOrCreateIfNotExistsForCourse(course, user);
        rateLimitService.checkRateLimitElseThrow(session, user);

        // TODO: ÜBERARBEITEN !!!

        CompletableFuture.runAsync(() -> executeCoursePipeline(session, competencyJol));
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval — programming exercise
    // -------------------------------------------------------------------------

    private IrisChatSession getCurrentSessionOrCreateIfNotExistsForExercise(Exercise exercise, User user) {
        return irisChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst()
                .orElseGet(() -> createExerciseSessionInternal(exercise, user));
    }

    /**
     * Gets or creates the current Iris session for the given exercise and user.
     *
     * @param exercise The exercise
     * @param user     The user
     * @return The current (or newly created) Iris session
     */
    public IrisChatSession getCurrentSessionOrCreateIfNotExists(Exercise exercise, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course != null) {
            irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        }
        return getCurrentSessionOrCreateIfNotExistsForExercise(exercise, user);
    }

    /**
     * Creates a new Iris session for the given exercise (any type) and user.
     *
     * @param exercise The exercise
     * @param user     The user
     * @return The created session
     */
    public IrisChatSession createSession(Exercise exercise, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course != null) {
            irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        }
        return createExerciseSessionInternal(exercise, user);
    }

    private IrisChatSession createExerciseSessionInternal(Exercise exercise, User user) {
        if (exercise.isExamExercise()) {
            throw new IrisUnsupportedExerciseTypeException("Iris is not supported for exam exercises");
        }
        var session = new IrisChatSession(exercise, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval — course
    // -------------------------------------------------------------------------

    /**
     * Gets or creates the current Iris course chat session for the given course and user.
     * If the last session is from a different day, a new one is created.
     *
     * @param course The course
     * @param user   The user
     * @return The current (or newly created) Iris session
     */
    public IrisChatSession getCurrentSessionOrCreateIfNotExists(Course course, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        return getCurrentSessionOrCreateIfNotExistsForCourse(course, user);
    }

    private IrisChatSession getCurrentSessionOrCreateIfNotExistsForCourse(Course course, User user) {
        var sessionOptional = irisChatSessionRepository.findLatestCourseChatSessionsByUserIdWithMessages(course.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();

            // if session is of today we can continue it; otherwise create a new one
            if (session.getCreationDate().withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now(ZoneId.systemDefault()))) {
                checkHasAccessTo(user, session);
                return session;
            }
        }
        return createCourseSessionInternal(course, user);
    }

    /**
     * Creates a new Iris course chat session for the given course and user.
     *
     * @param course The course
     * @param user   The user
     * @return The created session
     */
    // TODO: Nicht genutzt ?
    public IrisChatSession createSession(Course course, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        return createCourseSessionInternal(course, user);
    }

    private IrisChatSession createCourseSessionInternal(Course course, User user) {
        var session = new IrisChatSession(course, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }

    // -------------------------------------------------------------------------
    // Session creation / retrieval — lecture
    // -------------------------------------------------------------------------

    /**
     * Gets or creates the current Iris lecture chat session for the given lecture and user.
     *
     * @param lecture The lecture
     * @param user    The user
     * @return The current (or newly created) Iris session
     */
    public IrisChatSession getCurrentSessionOrCreateIfNotExists(Lecture lecture, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        var api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        irisSettingsService.ensureEnabledForCourseOrElseThrow(api.findByIdElseThrow(lecture.getId()).getCourse());
        return getCurrentSessionOrCreateIfNotExistsForLecture(lecture, user);
    }

    private IrisChatSession getCurrentSessionOrCreateIfNotExistsForLecture(Lecture lecture, User user) {
        return irisChatSessionRepository.findLatestByLectureIdAndUserIdWithMessages(lecture.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst()
                .orElseGet(() -> createLectureSessionInternal(lecture, user));
    }

    /**
     * Creates a new Iris lecture chat session for the given lecture and user.
     *
     * @param lecture The lecture
     * @param user    The user
     * @return The created session
     */
    public IrisChatSession createSession(Lecture lecture, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        var api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        irisSettingsService.ensureEnabledForCourseOrElseThrow(api.findByIdElseThrow(lecture.getId()).getCourse());
        return createLectureSessionInternal(lecture, user);
    }

    private IrisChatSession createLectureSessionInternal(Lecture lecture, User user) {
        var session = new IrisChatSession(lecture, user);
        session.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        return irisChatSessionRepository.save(session);
    }
}
