package de.tum.in.www1.artemis.service.iris.session;

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
public class IrisChatSessionService implements IrisSessionSubServiceInterface {

    private final Logger log = LoggerFactory.getLogger(IrisChatSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisWebsocketService irisWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    public IrisChatSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisWebsocketService irisWebsocketService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            SubmissionRepository submissionRepository, StudentParticipationRepository studentParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisWebsocketService = irisWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.submissionRepository = submissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;

    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param session The session to check
     * @param user    The user to check
     */
    @Override
    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        var chatSession = castToSessionType(session, IrisChatSession.class);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, chatSession.getExercise(), user);
        if (!Objects.equals(chatSession.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsIrisActivated(IrisSession session) {
        var chatSession = castToSessionType(session, IrisChatSession.class);
        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(chatSession.getExercise());
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisSession session) {
        var fullSession = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        Map<String, Object> parameters = Map.of();
        /*
         * if(fullSession instanceof IrisChatSession) {
         * ProgrammingExercise exercise = ((IrisChatSession) fullSession).getExercise();
         * parameters.put("exercise", exercise);
         * parameters.put("course", exercise.getCourseViaExerciseGroupOrCourseMember());
         * var participations = studentParticipationRepository.findByExerciseIdAndStudentId(exercise.getId(), ((IrisChatSession) fullSession).getUser().getId());
         * parameters.put("submission", submissionRepository.findByIdWithResultsElseThrow(participations.get(participations.size() - 1).getId()));
         * parameters.put("participation", participations.get(participations.size() - 1));
         * }
         * parameters.put("session", fullSession);
         */

        irisConnectorService.sendRequest(0, IrisModel.GPT3_5, parameters).handleAsync((irisMessage, throwable) -> {
            if (throwable != null) {
                log.error("Error while getting response from Iris model", throwable);
            }
            else if (irisMessage != null) {
                var irisMessageSaved = irisMessageService.saveMessage(irisMessage.message(), fullSession, IrisMessageSender.LLM);
                irisWebsocketService.sendMessage(irisMessageSaved);
            }
            else {
                log.error("No response from Iris model");
            }
            return null;
        });
    }
}
