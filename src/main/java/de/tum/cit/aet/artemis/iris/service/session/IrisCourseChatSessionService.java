package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

/**
 * Service to handle the course chat subsystem of Iris.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisCourseChatSessionService extends AbstractIrisChatSessionService<IrisCourseChatSession> {

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final CourseRepository courseRepository;

    private final MessageSource messageSource;

    public IrisCourseChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService, IrisCourseChatSessionRepository irisCourseChatSessionRepository,
            PyrisPipelineService pyrisPipelineService, ObjectMapper objectMapper, CourseRepository courseRepository, MessageSource messageSource) {
        super(irisSessionRepository, null, null, objectMapper, irisMessageService, irisMessageRepository, irisChatWebsocketService, llmTokenUsageService);
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.courseRepository = courseRepository;
        this.messageSource = messageSource;
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the course and the session belongs to them.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisCourseChatSession session) {
        user.hasAcceptedExternalLLMUsageElseThrow();
        var course = courseRepository.findByIdElseThrow(session.getCourseId());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        if (!Objects.equals(session.getUserId(), user.getId())) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisCourseChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIrisEnabledFor(IrisCourseChatSession session) {
        var course = courseRepository.findByIdElseThrow(session.getCourseId());
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
    }

    @Override
    public void sendOverWebsocket(IrisCourseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void checkRateLimit(User user, IrisCourseChatSession session) {
        rateLimitService.checkRateLimitElseThrow(session, user);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisCourseChatSession session) {
        var course = courseRepository.findByIdElseThrow(session.getCourseId());
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this course", "Iris", "irisDisabled");
        }
        requestAndHandleResponse(session, settings.variant().jsonValue(), settings.customInstructions(), null);
    }

    private void requestAndHandleResponse(IrisCourseChatSession session, String variant, String customInstructions, Object object) {
        var chatSession = (IrisCourseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        pyrisPipelineService.executeCourseChatPipeline(variant, customInstructions, chatSession, object);
    }

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisCourseChatSession session) {
        builder.withCourse(session.getCourseId());
    }

    /**
     * Handles the CompetencyJolSetEvent by checking if Iris is activated for the course and if the user has accepted external LLM usage.
     * If both conditions are met, it retrieves or creates a session and sends the request to the LLM.
     *
     * @param competencyJolSetEvent The event containing the CompetencyJol
     */
    public void handleCompetencyJolSetEvent(CompetencyJolSetEvent competencyJolSetEvent) {
        var competencyJol = competencyJolSetEvent.getEventObject();
        var course = competencyJol.getCompetency().getCourse();
        var user = competencyJol.getUser();

        if (!user.hasAcceptedExternalLLMUsage()) {
            return;
        }

        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            return;
        }

        var session = getCurrentSessionOrCreateIfNotExistsInternal(course, user, false);
        rateLimitService.checkRateLimitElseThrow(session, user);

        var variant = settings.variant().jsonValue();
        var customInstructions = settings.customInstructions();

        CompletableFuture.runAsync(() -> requestAndHandleResponse(session, variant, customInstructions, competencyJol));
    }

    /**
     * Gets the current Iris session for the course and user.
     * If no session exists or if the last session is from a different day, a new one is created.
     *
     * @param course                      The course to get the session for
     * @param user                        The user to get the session for
     * @param sendInitialMessageIfCreated Whether to send an initial message from Iris if a new session is created
     * @return The current Iris session
     */
    public IrisCourseChatSession getCurrentSessionOrCreateIfNotExists(Course course, User user, boolean sendInitialMessageIfCreated) {
        user.hasAcceptedExternalLLMUsageElseThrow();
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        return getCurrentSessionOrCreateIfNotExistsInternal(course, user, sendInitialMessageIfCreated);
    }

    private IrisCourseChatSession getCurrentSessionOrCreateIfNotExistsInternal(Course course, User user, boolean sendInitialMessageIfCreated) {
        var sessionOptional = irisCourseChatSessionRepository.findLatestByCourseIdAndUserIdWithMessages(course.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();

            // if session is of today we can continue it; otherwise create a new one
            if (session.getCreationDate().withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now(ZoneId.systemDefault()))) {
                checkHasAccessTo(user, session);
                return session;
            }
        }

        // create a new session with an initial message from Iris
        return createSessionInternal(course, user, sendInitialMessageIfCreated);
    }

    /**
     * Creates a new Iris session for the course and user.
     *
     * @param course             The course to create the session for
     * @param user               The user to create the session for
     * @param sendInitialMessage Whether to send an initial message from Iris
     * @return The created Iris session
     */
    public IrisCourseChatSession createSession(Course course, User user, boolean sendInitialMessage) {
        user.hasAcceptedExternalLLMUsageElseThrow();
        irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        return createSessionInternal(course, user, sendInitialMessage);
    }

    private IrisCourseChatSession createSessionInternal(Course course, User user, boolean sendInitialMessage) {
        var courseChat = new IrisCourseChatSession(course, user);
        courseChat.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        var session = irisCourseChatSessionRepository.save(courseChat);

        if (sendInitialMessage) {
            // Run async to allow the session to be returned immediately
            CompletableFuture.runAsync(() -> requestAndHandleResponse(session));
        }

        return session;
    }
}
