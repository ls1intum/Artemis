package de.tum.in.www1.artemis.service.iris.session;

import java.util.*;

import javax.annotation.Nullable;
import javax.ws.rs.BadRequestException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.message.*;
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
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.websocket.IrisCodeEditorWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

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

    private final IrisCodeEditorWebsocketService irisCodeEditorWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            GitService gitService, RepositoryService repositoryService) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    /**
     * Checks if the user has access to the Iris session. A user has access if they have access to the exercise and the
     * session belongs to them. If the user is null, the user is fetched from the database.
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
     * Sends a request containing the current state of the exercise repositories in the code editor and the entire
     * conversation history to the LLM, and handles the response.
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
        irisConnectorService.sendRequestV2(IrisConstants.CODE_EDITOR_INITIAL_REQUEST, "gpt-4-32k", params).handleAsync((response, err) -> {
            if (err != null) {
                log.error("Error while getting response from Iris model", err);
                irisCodeEditorWebsocketService.sendException(session, err.getCause());
            }
            else if (response == null) {
                log.error("No response from Iris model");
                irisCodeEditorWebsocketService.sendException(session, new IrisNoResponseException());
            }
            else {
                try {
                    var irisMessage = toIrisMessage(response.content());
                    var saved = irisMessageService.saveMessage(irisMessage, session, IrisMessageSender.LLM);
                    irisCodeEditorWebsocketService.sendMessage(saved);
                }
                catch (IrisParseResponseException e) {
                    irisCodeEditorWebsocketService.sendException(session, e);
                }
            }
            return null;
        });
    }

    private static IrisMessage toIrisMessage(JsonNode content) throws IrisParseResponseException {
        var message = new IrisMessage();
        if (!content.hasNonNull("response"))
            throw new IrisParseResponseException(new Throwable("No response"));
        var chatWindowResponse = content.get("response").asText();
        message.addContent(new IrisTextMessageContent(message, chatWindowResponse));

        if (content.hasNonNull("components"))
            message.addContent(toExercisePlan(content));

        return message;
    }

    /**
     * Converts a JsonNode into an IrisExercisePlanMessageContent. In order for this to succeed, the JsonNode must have
     * the following structure:
     *
     * <pre>
     *     {
     *          "components": [
     *              {
     *                  "component": "problem statement",
     *                  "plan": "..."
     *              },
     *              {
     *                  "component": "solution",
     *                  "plan": "..."
     *              },
     *              {
     *                  "component": "template",
     *                  "plan": "..."
     *              },
     *              {
     *                  "component": "test",
     *                  "plan": "..."
     *              }
     *          ]
     *     }
     * </pre>
     *
     * @param content The JsonNode to convert
     * @return The converted IrisExercisePlanMessageContent
     * @throws IrisParseResponseException If the JsonNode does not have the correct structure
     */
    private static IrisExercisePlanMessageContent toExercisePlan(JsonNode content) throws IrisParseResponseException {
        if (!content.get("components").isArray()) {
            throw new IrisParseResponseException(new Throwable("Exercise plan components is not an array"));
        }
        var exercisePlan = new IrisExercisePlanMessageContent();
        List<IrisExercisePlanComponent> components = new ArrayList<>();
        for (JsonNode node : content.get("components")) {
            if (!node.hasNonNull("plan")) {
                continue; // This might happen as a result of the LLM deciding to stop generating components, which is OK
            }
            var plan = node.get("plan").asText();
            ExerciseComponent component = switch (node.get("component").asText("")) {
                case "problem statement" -> ExerciseComponent.PROBLEM_STATEMENT;
                case "solution" -> ExerciseComponent.SOLUTION_REPOSITORY;
                case "template" -> ExerciseComponent.TEMPLATE_REPOSITORY;
                case "test" -> ExerciseComponent.TEST_REPOSITORY;
                default -> throw new IrisParseResponseException(new Throwable("Unknown exercise plan component"));
            };
            components.add(new IrisExercisePlanComponent(exercisePlan, component, plan));
        }
        if (components.isEmpty()) {
            throw new IrisParseResponseException(new Throwable("No exercise plan components"));
        }
        exercisePlan.setComponents(components);
        return exercisePlan;
    }

    public void requestExerciseChanges(IrisCodeEditorSession session, IrisExercisePlanMessageContent exercisePlan) {
        if (!exercisePlan.hasNext()) {
            throw new BadRequestException("Exercise plan does not have any more instructions");
        }
        exercisePlan.setExecuting(true);
        // Continue to execute the plan until there are no more components or the plan has been paused externally
        while (exercisePlan.hasNext() && exercisePlan.isExecuting()) {
            var nextPlanComponent = exercisePlan.next();
            var component = nextPlanComponent.getComponent();
            // TODO: Add prompt for each component
            IrisTemplate prompt = switch (component) {
                case PROBLEM_STATEMENT -> null;
                case SOLUTION_REPOSITORY -> null;
                case TEMPLATE_REPOSITORY -> null;
                case TEST_REPOSITORY -> null;
            };
            var exercise = session.getExercise();
            var params = new HashMap<String, Object>();
            params.put("instructions", nextPlanComponent.getInstructions());
            params.put("problemStatement", exercise.getProblemStatement());
            params.put("solutionRepository", getRepositoryContents(exercise.getVcsSolutionRepositoryUrl()));
            params.put("templateRepository", getRepositoryContents(exercise.getVcsTemplateRepositoryUrl()));
            params.put("testRepository", getRepositoryContents(exercise.getVcsTestRepositoryUrl()));
            irisConnectorService.sendRequestV2(prompt, "gpt-4-32k", params).handleAsync((response, err) -> {
                if (err != null) {
                    log.error("Error while getting response from Iris model", err);
                    irisCodeEditorWebsocketService.sendException(session, err.getCause());
                }
                else if (response == null) {
                    log.error("No response from Iris model");
                    irisCodeEditorWebsocketService.sendException(session, new IrisNoResponseException());
                }
                else {
                    try {
                        var changes = extractChanges(response.content());
                        // In this case we do not save anything, as these changes must first be approved by the user
                        irisCodeEditorWebsocketService.sendChanges(session, component, changes);
                    }
                    catch (IrisParseResponseException e) {
                        irisCodeEditorWebsocketService.sendException(session, e);
                    }
                }
                return null;
            });
        }
        // This may have already been the reason for exiting the loop, but set it here in case it wasn't
        exercisePlan.setExecuting(false);
    }

    private static List<IrisCodeEditorWebsocketService.FileChange> extractChanges(JsonNode content) throws IrisParseResponseException {
        // TODO: Implement
        return null;
    }

    /**
     * Extract the contents of a repository as a Map<String, String> of file paths to file contents.
     *
     * @param vcsRepositoryUrl The URL of the repository to extract
     * @return The contents of the repository
     */
    private Map<String, String> getRepositoryContents(@Nullable VcsRepositoryUrl vcsRepositoryUrl) {
        return Optional.ofNullable(vcsRepositoryUrl).map(url -> {
            try {
                return gitService.getOrCheckoutRepository(url, true);
            }
            catch (GitAPIException e) {
                throw new InternalServerErrorException("Could not get or checkout exercise repository");
            }
        }).map(repositoryService::getFilesWithContent).orElse(Map.of());
    }

}
