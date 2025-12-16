package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Lazy
@Service
@Profile(PROFILE_IRIS)
// now extending AbstractIrisChatSessionService
public class IrisLectureChatSessionService extends AbstractIrisChatSessionService<IrisLectureChatSession> {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService irisRateLimitService;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final PyrisPipelineService pyrisPipelineService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    public IrisLectureChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            Optional<LectureRepositoryApi> lectureRepositoryApi, PyrisPipelineService pyrisPipelineService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService, ObjectMapper objectMapper) {
        super(irisSessionRepository, null, null, objectMapper, irisMessageService, irisMessageRepository, irisChatWebsocketService, llmTokenUsageService);
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisRateLimitService = rateLimitService;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.pyrisPipelineService = pyrisPipelineService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
    }

    /*
     * comparable to IrisCourseChatSessionService.java without:
     * - CompetencyJOL and its methods getting/creating sessions
     * - IrisCourseChatSessionRepository (used for CompetencyJOL methods)
     * - MessageSource (used for CompetencyJOL methods)
     */

    @Override
    public void sendOverWebsocket(IrisLectureChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisLectureChatSession session) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));

        var chatSession = (IrisLectureChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        var lecture = api.findByIdWithLectureUnitsElseThrow(chatSession.getLectureId());
        var course = lecture.getCourse();

        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false).irisLectureChatSettings();
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this lecture", "Iris", "irisDisabled");
        }

        pyrisPipelineService.executeLectureChatPipeline(settings.selectedVariant(), settings.customInstructions(), chatSession, lecture);
    }

    // uses method of the superclass
    // needs this wrapper compared to IrisCourseChatSessionService because of differing status dtos
    public TrackedSessionBasedPyrisJob handleStatusUpdate(LectureChatJob job, PyrisLectureChatStatusUpdateDTO statusUpdate) {
        var enrichedUpdate = new PyrisChatStatusUpdateDTO(statusUpdate.result(), statusUpdate.stages(), statusUpdate.sessionTitle(), null, null, null, null);
        return handleStatusUpdate(job, enrichedUpdate);
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the lecture and the session belongs to them.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisLectureChatSession session) {
        user.hasAcceptedExternalLLMUsageElseThrow();
        if (session.getUserId() != user.getId()) {
            throw new AccessForbiddenException("Iris Lecture chat Session", session.getId());
        }

        var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(session.getLectureId());
        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the lecture and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param user    The user to check
     * @param session The session to check
     * @return weather the user has access to the session
     */
    public boolean hasAccess(User user, IrisLectureChatSession session) {
        try {
            checkHasAccessTo(user, session);
            return true;
        }
        catch (AccessForbiddenException e) {
            return false;
        }
    }

    /**
     * Checks if the lecture connected to IrisCourseChatSession has Iris enabled.
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisLectureChatSession session) {
        var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(session.getLectureId());
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.LECTURE_CHAT, lecture.getCourse());
    }

    @Override
    public void checkRateLimit(User user) {
        irisRateLimitService.checkRateLimitElseThrow(user);
    }

    // now needed since extending AbstractIrisChatSessionService
    // associated token usage to type of the chat
    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisLectureChatSession session) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        Lecture lecture = api.findByIdElseThrow(session.getLectureId());
        if (lecture.getCourse() != null) {
            builder.withCourse(lecture.getCourse().getId());
        }
    }
}
