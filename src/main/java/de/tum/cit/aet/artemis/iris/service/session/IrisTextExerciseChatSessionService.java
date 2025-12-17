package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Comparator;
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
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.text.api.TextApi;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisTextExerciseChatSessionService
        implements IrisChatBasedFeatureInterface<IrisTextExerciseChatSession>, IrisRateLimitedFeatureInterface<IrisTextExerciseChatSession> {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final IrisMessageService irisMessageService;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final StudentParticipationRepository studentParticipationRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final PyrisJobService pyrisJobService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public IrisTextExerciseChatSessionService(IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            IrisMessageService irisMessageService, Optional<TextRepositoryApi> textRepositoryApi, StudentParticipationRepository studentParticipationRepository,
            PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
        this.irisMessageService = irisMessageService;
        this.textRepositoryApi = textRepositoryApi;
        this.studentParticipationRepository = studentParticipationRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    @Override
    public void sendOverWebsocket(IrisTextExerciseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void requestAndHandleResponse(IrisTextExerciseChatSession irisSession) {
        var session = (IrisTextExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        var exercise = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class)).findByIdElseThrow(session.getExerciseId());
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var settings = course == null ? IrisCourseSettingsDTO.defaultSettings() : irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled()) {
            throw new ConflictException("Iris is not enabled for this exercise", "Iris", "irisDisabled");
        }
        var user = userRepository.findByIdElseThrow(session.getUserId());
        // TODO: Once we can receive client form data through the IrisMessageResource, we should use that instead of fetching the latest submission to get the text
        var participation = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        var latestSubmission = participation.flatMap(p -> p.getSubmissions().stream().max(Comparator.comparingLong(Submission::getId))).orElse(null);
        String latestSubmissionText;
        if (latestSubmission instanceof TextSubmission textSubmission) {
            latestSubmissionText = textSubmission.getText();
        }
        else {
            latestSubmissionText = null;
        }
        var chatHistory = session.getMessages().stream().map(PyrisMessageDTO::of).toList();
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "text-exercise-chat",
                user.getSelectedLLMUsage(),
                settings.variant().jsonValue(),
                Optional.empty(),
                pyrisJobService.createTokenForJob(token -> new TextExerciseChatJob(token, course.getId(), exercise.getId(), session.getId())),
                dto -> new PyrisTextExerciseChatPipelineExecutionDTO(PyrisTextExerciseDTO.of(exercise), session.getTitle(), chatHistory, new PyrisUserDTO(user), latestSubmissionText, dto.settings(), dto.initialStages(), settings.customInstructions()),
                stages -> irisChatWebsocketService.sendMessage(session, null, stages)
        );
    }

    /**
     * Handles the status update of a text exercise chat job.
     *
     * @param job          The job that is updated
     * @param statusUpdate The status update
     * @return The same job that was passed in
     */
    public TextExerciseChatJob handleStatusUpdate(TextExerciseChatJob job, PyrisTextExerciseChatStatusUpdateDTO statusUpdate) {
        // TODO: LLM Token Tracking - or better, make this class a subclass of AbstractIrisChatSessionService
        var session = (IrisTextExerciseChatSession) irisSessionRepository.findByIdElseThrow(job.sessionId());
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

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisTextExerciseChatSession session) {
        // TODO: This check is probably unnecessary since we are fetching the sessions from the database with the user ID already
        if (session.getUserId() != user.getId()) {
            throw new AccessForbiddenException("Iris Text Exercise Chat Session", session.getId());
        }
        var exercise = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class)).findByIdElseThrow(session.getExerciseId());
        // TODO: This check is probably unnecessary as the endpoint already checks it via the @EnforceAtLeastStudentInExercise annotation
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
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
        try {
            checkHasAccessTo(user, session);
            return true;
        }
        catch (AccessForbiddenException e) {
            return false;
        }
    }

    @Override
    public void checkIrisEnabledFor(IrisTextExerciseChatSession session) {
        var exercise = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class)).findByIdElseThrow(session.getExerciseId());
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course != null) {
            irisSettingsService.ensureEnabledForCourseOrElseThrow(course);
        }
    }

    @Override
    public void checkRateLimit(User user, IrisTextExerciseChatSession session) {
        rateLimitService.checkRateLimitElseThrow(session, user);
    }
}
