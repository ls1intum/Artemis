package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;

@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisLectureChatSessionService implements IrisChatBasedFeatureInterface<IrisLectureChatSession>, IrisRateLimitedFeatureInterface<IrisLectureChatSession> {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService irisRateLimitService;

    private final IrisMessageService irisMessageService;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final PyrisPipelineService pyrisPipelineService;

    private final PyrisJobService pyrisJobService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public IrisLectureChatSessionService(IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            IrisMessageService irisMessageService, Optional<LectureRepositoryApi> lectureRepositoryApi, PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService,
            IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisRateLimitService = rateLimitService;
        this.irisMessageService = irisMessageService;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    @Override
    public void sendOverWebsocket(IrisLectureChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    // This is the message from the user sent to Iris
    @Override
    public void requestAndHandleResponse(IrisLectureChatSession lectureChatSession) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));

        var session = (IrisLectureChatSession) irisSessionRepository.findByIdWithMessagesAndContents(lectureChatSession.getId());
        var lecture = api.findByIdElseThrow(session.getLectureId());
        var course = lecture.getCourse();
        var user = userRepository.findByIdElseThrow(session.getUserId());

        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this lecture", "Iris", "irisDisabled");
        }

        var chatHistory = session.getMessages().stream().map(PyrisMessageDTO::of).toList();
        pyrisPipelineService.executePipeline("lecture-chat", settings.variant().jsonValue(), Optional.empty(),
                pyrisJobService.createTokenForJob(token -> new LectureChatJob(token, course.getId(), lecture.getId(), session.getId())),
                dto -> new PyrisLectureChatPipelineExecutionDTO(course.getId(), lecture.getId(), session.getTitle(), chatHistory, new PyrisUserDTO(user), dto.settings(),
                        dto.initialStages(), settings.customInstructions()),
                stages -> irisChatWebsocketService.sendMessage(session, null, stages));
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
     * Handles the status update of a lecture chat job.
     *
     * @param job          The job that is updated
     * @param statusUpdate The status update
     * @return The same job that was passed in
     */
    public LectureChatJob handleStatusUpdate(LectureChatJob job, PyrisLectureChatStatusUpdateDTO statusUpdate) {
        // TODO: LLM Token Tracking - or better, make this class a subclass of AbstractIrisChatSessionService
        var session = (IrisLectureChatSession) irisSessionRepository.findByIdElseThrow(job.sessionId());
        String sessionTitle = AbstractIrisChatSessionService.setSessionTitle(session, statusUpdate.sessionTitle(), irisSessionRepository);
        if (statusUpdate.result() != null) {
            var message = session.newMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            IrisMessage savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages(), sessionTitle);
        }
        else {
            irisChatWebsocketService.sendMessage(session, null, statusUpdate.stages(), sessionTitle);
        }

        return job;
    }

    @Override
    public void checkIrisEnabledFor(IrisLectureChatSession session) {
        var lecture = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class)).findByIdElseThrow(session.getLectureId());
        irisSettingsService.ensureEnabledForCourseOrElseThrow(lecture.getCourse());
    }

    @Override
    public void checkRateLimit(User user, IrisLectureChatSession session) {
        irisRateLimitService.checkRateLimitElseThrow(session, user);
    }
}
