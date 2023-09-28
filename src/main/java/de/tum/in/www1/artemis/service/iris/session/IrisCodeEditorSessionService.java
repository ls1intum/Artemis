package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.ws.rs.BadRequestException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
@Profile("iris")
public class IrisCodeEditorSessionService implements IrisSessionSubServiceInterface {

    private final Logger log = LoggerFactory.getLogger(IrisCodeEditorSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService; // Will need this when we have settings to consider

    private final IrisWebsocketService irisWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService,
                                        IrisSettingsService irisSettingsService, IrisWebsocketService irisWebsocketService,
                                        AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
                                        GitService gitService, RepositoryService repositoryService) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisWebsocketService = irisWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
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
        checkHasAccessToIrisSession(castToSessionType(session, IrisCodeEditorSession.class), user);
    }
    
    private void checkHasAccessToIrisSession(IrisCodeEditorSession session, User user) {
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, session.getExercise(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Code Editor Session", session.getId());
        }
    }

    @Override
    public void checkIsIrisActivated(IrisSession session) {
        checkIsIrisActivated(castToSessionType(session, IrisCodeEditorSession.class));
    }
    
    private void checkIsIrisActivated(IrisCodeEditorSession ignored) {
        // Code editor sessions should probably be available for every programming exercise, especially just-created ones
        // However, we still may want to check something here
        // Await Timor's settings system update PR
    }
    
    @Override
    public void requestAndHandleResponse(IrisSession irisSession) {
        // Don't bother casting now as we have to fetch the session by ID from the database anyway
        requestAndHandleResponse(irisSession.getId());
    }
    
    /**
     * Sends a request containing the current state of the exercise open in the code editor
     * and the entire conversation history to the LLM, and handles the response.
     *
     * @param sessionId The id of the session to send to the LLM
     */
    private void requestAndHandleResponse(Long sessionId) {
        var fromDB = irisSessionRepository.findByIdWithMessagesAndContents(sessionId);
        if (!(fromDB instanceof IrisCodeEditorSession session)) {
            throw new BadRequestException("Iris session is not a code editor session");
        }
        var exercise = session.getExercise();
        var params = new HashMap<String, Object>();
        params.put("chat_history", session.getMessages());
        params.put("ps", exercise.getProblemStatement());
        params.put("solution_repo", getRepositoryContents(exercise.getVcsSolutionRepositoryUrl()));
        params.put("template_repo", getRepositoryContents(exercise.getVcsTemplateRepositoryUrl()));
        params.put("test_repo", getRepositoryContents(exercise.getVcsTestRepositoryUrl()));
        
        irisConnectorService.sendRequest(new IrisTemplate("TODO"), "gpt-4-32k", params)
                .handleAsync((responseMessage, err) -> {
                    if (err != null) {
                        log.error("Error while getting response from Iris model", err);
                        irisWebsocketService.sendException(session, err.getCause());
                    }
                    else if (responseMessage == null) {
                        log.error("No response from Iris model");
                        irisWebsocketService.sendException(session, new IrisNoResponseException());
                    }
                    else {
                        var irisMessageSaved = irisMessageService.saveMessage(responseMessage.message(), session, IrisMessageSender.LLM);
                        irisWebsocketService.sendMessage(irisMessageSaved);
                    }
                    return null;
                });
    }
    
    private Map<String, String> getRepositoryContents(@Nullable VcsRepositoryUrl vcsRepositoryUrl) {
        return Optional.ofNullable(vcsRepositoryUrl)
                .map(url -> {
                    try {
                        return gitService.getOrCheckoutRepository(url, true);
                    } catch (GitAPIException e) {
                        throw new InternalServerErrorException("Could not get or checkout exercise repository");
                    }
                })
                .map(repositoryService::getFilesWithContent)
                .orElse(Map.of());
    }

}
