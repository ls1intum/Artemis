package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisExerciseChatSessionService extends AbstractIrisChatSessionService<IrisExerciseChatSession> implements IrisRateLimitedFeatureInterface {

    private final IrisMessageService irisMessageService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public IrisExerciseChatSessionService(IrisMessageService irisMessageService, LLMTokenUsageService llmTokenUsageService, IrisSettingsService irisSettingsService,
            IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, ProgrammingExerciseRepository programmingExerciseRepository,
            ObjectMapper objectMapper) {
        super(irisSessionRepository, objectMapper);
        this.irisMessageService = irisMessageService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    public IrisExerciseChatSession createChatSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return irisSessionRepository.save(new IrisExerciseChatSession(exercise, user));
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisExerciseChatSession session) {
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, session.getExercise(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisExerciseChatSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, session.getExercise());
    }

    @Override
    public void sendOverWebsocket(IrisExerciseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisExerciseChatSession session) {
        var chatSession = (IrisExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        if (chatSession.getExercise().isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(chatSession.getExercise().getId());
        var latestSubmission = getLatestSubmissionIfExists(exercise, chatSession.getUser());

        var variant = irisSettingsService.getCombinedIrisSettingsFor(session.getExercise(), false).irisChatSettings().selectedVariant();
        pyrisPipelineService.executeExerciseChatPipeline(variant, latestSubmission, exercise, chatSession);
    }

    private Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        var participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(sub.getId()));
    }

    /**
     * Handles the status update of a ExerciseChatJob by sending the result to the student via the Websocket.
     *
     * @param job          The job that was executed
     * @param statusUpdate The status update of the job
     */
    public void handleStatusUpdate(ExerciseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var session = (IrisExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
        IrisMessage savedMessage = null;
        if (statusUpdate.result() != null) {
            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
        }
        else {
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), statusUpdate.suggestions(), statusUpdate.tokens());
        }

        if (statusUpdate.tokens() != null) {
            llmTokenUsageService.saveIrisTokenUsage(job, savedMessage, session.getExercise(), session.getUser(), session.getExercise().getCourseViaExerciseGroupOrCourseMember(),
                    statusUpdate.tokens());
        }

        updateLatestSuggestions(session, statusUpdate.suggestions());
    }
}
