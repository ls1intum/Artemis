package de.tum.in.www1.artemis.service.iris.session;

import java.util.Comparator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisTextExerciseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.text_exercise.PyrisTextExerciseChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.text_exercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TextExerciseChatJob;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@Service
@Profile("iris")
public class IrisTextExerciseChatSessionService implements IrisChatBasedFeatureInterface<IrisTextExerciseChatSession>, IrisRateLimitedFeatureInterface {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final IrisMessageService irisMessageService;

    private final TextExerciseRepository textExerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final PyrisJobService pyrisJobService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    public IrisTextExerciseChatSessionService(IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            IrisMessageService irisMessageService, TextExerciseRepository textExerciseRepository, StudentParticipationRepository studentParticipationRepository,
            PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService) {
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
        this.irisMessageService = irisMessageService;
        this.textExerciseRepository = textExerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
    }

    @Override
    public void sendOverWebsocket(IrisTextExerciseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void requestAndHandleResponse(IrisTextExerciseChatSession irisSession) {
        var session = (IrisTextExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        if (session.getExercise().isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var exercise = textExerciseRepository.findByIdElseThrow(session.getExercise().getId());
        if (!irisSettingsService.isEnabledFor(IrisSubSettingsType.TEXT_EXERCISE_CHAT, exercise)) {
            throw new ConflictException("Iris is not enabled for this exercise", "Iris", "irisDisabled");
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        // TODO: Once we can receive client form data through the IrisMessageResource, we should use that instead of fetching the latest submission to get the text
        var participation = studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), session.getUser().getLogin());
        var latestSubmission = participation.flatMap(p -> p.getSubmissions().stream().max(Comparator.comparingLong(Submission::getId))).orElse(null);
        String latestSubmissionText;
        if (latestSubmission instanceof TextSubmission textSubmission) {
            latestSubmissionText = textSubmission.getText();
        }
        else {
            latestSubmissionText = null;
        }
        var conversation = session.getMessages().stream().map(PyrisMessageDTO::of).toList();
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "text-exercise-chat",
                "default",
                pyrisJobService.createTokenForJob(token -> new TextExerciseChatJob(token, course.getId(), exercise.getId(), session.getId())),
                dto -> new PyrisTextExerciseChatPipelineExecutionDTO(dto, new PyrisTextExerciseDTO(exercise), conversation, latestSubmissionText),
                stages -> irisChatWebsocketService.sendMessage(session, null, stages)
        );
        // @formatter:on
    }

    public void handleStatusUpdate(TextExerciseChatJob job, PyrisTextExerciseChatStatusUpdateDTO statusUpdate) {
        var session = (IrisTextExerciseChatSession) irisSessionRepository.findByIdElseThrow(job.sessionId());
        var message = session.newMessage();
        message.addContent(new IrisTextMessageContent(statusUpdate.result()));
        IrisMessage savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
        irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
    }

    @Override
    public void checkHasAccessTo(User user, IrisTextExerciseChatSession session) {
        if (!hasAccess(user, session)) {
            throw new AccessForbiddenException();
        }
    }

    /**
     * This method returns true if the user has access to the given session.
     * The user has access iff the user is a student in the exercise's course,
     * and they are the same user that created the session.
     *
     * @param user    The user to check
     * @param session The session to check
     * @return True if the user has access, false otherwise
     */
    public boolean hasAccess(User user, IrisTextExerciseChatSession session) {
        // TODO: This check is probably unnecessary since we are fetching the sessions from the database with the user ID already
        if (!session.getUser().equals(user)) {
            return false;
        }
        try {
            // TODO: This check is probably unnecessary as the endpoint already checks it via the @EnforceAtLeastStudentInExercise annotation
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, session.getExercise(), user);
        }
        catch (AccessForbiddenException e) {
            return false;
        }
        return true;
    }

    @Override
    public void checkIsFeatureActivatedFor(IrisTextExerciseChatSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.TEXT_EXERCISE_CHAT, session.getExercise());
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }
}
