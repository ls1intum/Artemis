package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisConstants;
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
 * Service to handle the code editor subsystem of Iris.
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
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, session.getExercise(), user);
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
        // FIXME: Await update to Iris settings system
    }
    
    /**
     * Sends a request containing the current state of the exercise repositories in the code editor
     * and the entire conversation history to the LLM, and handles the response.
     *
     * @param irisSession The session to send the request for
     */
    @Override
    public void requestAndHandleResponse(IrisSession irisSession) {
        var fromDB = irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        if (!(fromDB instanceof IrisCodeEditorSession session)) {
            throw new BadRequestException("Iris session is not a code editor session");
        }
        var exercise = session.getExercise();
        var params = new HashMap<String, Object>();
        params.put("chatHistory", session.getMessages());
        params.put("problemStatement", exercise.getProblemStatement());
        params.put("solutionRepository", getRepositoryContents(exercise.getVcsSolutionRepositoryUrl()));
        params.put("templateRepository", getRepositoryContents(exercise.getVcsTemplateRepositoryUrl()));
        params.put("testRepository", getRepositoryContents(exercise.getVcsTestRepositoryUrl()));
        
        // FIXME: Template and model should be be configurable; await settings update
        // The response handling is duplicated, also exists in IrisChatSessionService
        // However, there is no good reason why every session type should have the same response handling
        // TODO: Consider refactoring this
        irisConnectorService
                .sendRequest(new IrisTemplate(IrisConstants.CODE_EDITOR_INITIAL_REQUEST), "gpt-4-32k", params)
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
    
    public void requestExerciseChanges(IrisCodeEditorSession session, IrisExercisePlanMessageContent exercisePlan) {
        if (!exercisePlan.hasNext())
            throw new BadRequestException("Exercise plan does not have any more instructions");
        var nextInstruction = exercisePlan.next();
        exercisePlan.setExecuting(true);
        var prompt = switch (nextInstruction.getComponent()) {
            case PROBLEM_STATEMENT -> "TODO";
            case SOLUTION_REPOSITORY -> "TODO";
            case TEMPLATE_REPOSITORY -> "TODO";
            case TEST_REPOSITORY -> "TODO";
        };
        var exercise = session.getExercise();
        var params = new HashMap<String, Object>();
        params.put("chatHistory", session.getMessages());
        params.put("problemStatement", exercise.getProblemStatement());
        params.put("solutionRepository", getRepositoryContents(exercise.getVcsSolutionRepositoryUrl()));
        params.put("templateRepository", getRepositoryContents(exercise.getVcsTemplateRepositoryUrl()));
        params.put("testRepository", getRepositoryContents(exercise.getVcsTestRepositoryUrl()));
        irisConnectorService
                .sendRequest(new IrisTemplate(prompt), "gpt-4-32k", params)
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
                        // TODO: Apply changes to exercise
                    }
                    return null;
                });
    }
    
    /**
     * Extract the contents of a repository as a Map<String, String> of file paths to file contents.
     * @param vcsRepositoryUrl The URL of the repository to extract
     * @return The contents of the repository
     */
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
