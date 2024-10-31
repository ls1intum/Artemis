package de.tum.cit.aet.artemis.iris.service.session;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Service
@Profile("iris")
public class IrisLectureChatSessionService implements IrisChatBasedFeatureInterface<IrisLectureChatSession>, IrisRateLimitedFeatureInterface {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService irisRateLimitService;

    private final IrisMessageService irisMessageService;

    private final LectureRepository lectureRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final PyrisJobService pyrisJobService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    public IrisLectureChatSessionService(IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            IrisMessageService irisMessageService, LectureRepository lectureRepository, PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService,
            IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService) {
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisRateLimitService = rateLimitService;
        this.irisMessageService = irisMessageService;
        this.lectureRepository = lectureRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
    }

    @Override
    public void sendOverWebsocket(IrisLectureChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    // This is the message from the user sent to Iris
    @Override
    public void requestAndHandleResponse(IrisLectureChatSession lectureChatSession) {
        var session = (IrisLectureChatSession) irisSessionRepository.findByIdWithMessagesAndContents(lectureChatSession.getId());
        var lecture = lectureRepository.findByIdElseThrow(session.getLecture().getId());

        if (!irisSettingsService.isEnabledFor(IrisSubSettingsType.LECTURE_CHAT, lecture)) {
            throw new ConflictException("Iris is not enabled for this lecture", "Iris", "irisDisabled");
        }

        var course = lecture.getCourse();
        var conversation = session.getMessages().stream().map(PyrisMessageDTO::of).toList();
        System.out.println(conversation);
        pyrisPipelineService.executePipeline("lecture-chat", "default",
                pyrisJobService.createTokenForJob(token -> new LectureChatJob(token, course.getId(), lecture.getId(), session.getId())),
                dto -> new PyrisLectureChatPipelineExecutionDTO(new PyrisCourseDTO(course), conversation, new PyrisUserDTO(session.getUser()), dto.settings(), dto.initialStages()),
                stages -> irisChatWebsocketService.sendMessage(session, null, stages));
    }

    @Override
    public void checkHasAccessTo(User user, IrisLectureChatSession session) {
        if (!session.getUser().equals(user)) {
            throw new AccessForbiddenException("Iris Lecture chat Session", session.getId());
        }

        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, session.getLecture(), user);
    }

    public boolean hasAccess(User user, IrisLectureChatSession session) {
        try {
            checkHasAccessTo(user, session);
            return true;
        }
        catch (AccessForbiddenException e) {
            return false;
        }
    }

    public LectureChatJob handleStatusUpdate(LectureChatJob job, PyrisLectureChatStatusUpdateDTO statusUpdate) {
        // TODO: LLM Token Tracking - or better, make this class a subclass of AbstractIrisChatSessionService
        var session = (IrisLectureChatSession) irisSessionRepository.findByIdElseThrow(job.sessionId());
        if (statusUpdate.result() != null) {
            var message = session.newMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            IrisMessage savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
        }
        else {
            irisChatWebsocketService.sendMessage(session, null, statusUpdate.stages());
        }

        return job;
    }

    @Override
    public void checkIsFeatureActivatedFor(IrisLectureChatSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.LECTURE_CHAT, session.getLecture());
    }

    @Override
    public void checkRateLimit(User user) {
        irisRateLimitService.checkRateLimitElseThrow(user);
    }
}
