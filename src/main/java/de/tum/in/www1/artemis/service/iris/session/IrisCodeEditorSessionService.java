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
     * Extract the contents of a repository as a Map<String, String> of file paths to file contents.
     *
     * @param vcsRepositoryUrl The URL of the repository to extract
     * @return The contents of the repository
     */
    private Map<String, String> getRepositoryContents(@Nullable VcsRepositoryUrl vcsRepositoryUrl) {
        var contents = Optional.ofNullable(vcsRepositoryUrl).map(url -> {
            try {
                return gitService.getOrCheckoutRepository(url, true);
            }
            catch (GitAPIException e) {
                throw new InternalServerErrorException("Could not get or checkout exercise repository");
            }
        }).map(repositoryService::getFilesWithContent).map(HashMap::new).orElseGet(HashMap::new);
        contents.remove("readme.md");
        contents.remove(".gitignore");
        contents.remove(".gitattributes");
        contents.remove("gradlew");
        contents.remove("gradlew.bat");
        contents.entrySet().removeIf(entry -> entry.getKey().startsWith("gradle/wrapper"));
        return contents;
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
        var irisSettings = irisSettingsService.getCombinedIrisSettings(exercise, false);
        irisConnectorService.sendRequestV2(IrisConstants.CODE_EDITOR_INITIAL_REQUEST, irisSettings.getIrisChatSettings().getPreferredModel(), params)
                .handleAsync((response, err) -> {
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
                            log.error("Error while parsing response from Iris model", e);
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
                case "tests" -> ExerciseComponent.TEST_REPOSITORY;
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
            IrisTemplate prompt = switch (component) {
                case PROBLEM_STATEMENT -> IrisConstants.CODE_EDITOR_ADAPT_PROBLEM_STATEMENT;
                case SOLUTION_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_SOLUTION_REPOSITORY;
                case TEMPLATE_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_TEMPLATE_REPOSITORY;
                case TEST_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_TEST_REPOSITORY;
            };
            var exercise = session.getExercise();
            var params = new HashMap<String, Object>();
            params.put("instructions", nextPlanComponent.getInstructions());
            params.put("problemStatement", exercise.getProblemStatement());
            params.put("solutionRepository", getRepositoryContents(exercise.getVcsSolutionRepositoryUrl()));
            params.put("templateRepository", getRepositoryContents(exercise.getVcsTemplateRepositoryUrl()));
            params.put("testRepository", getRepositoryContents(exercise.getVcsTestRepositoryUrl()));
            irisConnectorService.sendRequestV2(prompt, "GPT3.5-turbo", params).handleAsync((response, err) -> {
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
                        var changes = extractChangesForComponent(response.content(), component);
                        // In this case we do not save anything, as these changes must first be approved by the user
                        irisCodeEditorWebsocketService.sendChanges(session, component, changes);
                    }
                    catch (IrisParseResponseException e) {
                        log.error("Error while parsing exercise changes from Iris model", e);
                        irisCodeEditorWebsocketService.sendException(session, e);
                    }
                }
                return null;
            });
        }
        // This may have already been the reason for exiting the loop, but set it here in case it wasn't
        exercisePlan.setExecuting(false);
    }

    /**
     * Extracts the changes for a specific component from the response of the LLM.
     * The response must have the following structure:
     *
     * <pre>
     *     {
     *         "changes": [
     *             {
     *                 "type": "modify",
     *                 "file": "path/to/file",
     *                 "original": "original file contents",
     *                 "updated": "updated file contents"
     *             },
     *             ...
     *         ]
     *     }
     * </pre>
     *
     * @param content   The JsonNode to extract the changes from
     * @param component The component to extract the changes for
     * @return The extracted changes
     * @throws IrisParseResponseException If the JsonNode does not have the correct structure
     */
    private static List<IrisCodeEditorWebsocketService.FileChange> extractChangesForComponent(JsonNode content, ExerciseComponent component) throws IrisParseResponseException {
        if (!content.get("changes").isArray()) {
            throw new IrisParseResponseException(new Throwable("Array of exercise changes was not present in response"));
        }
        List<IrisCodeEditorWebsocketService.FileChange> changes = new ArrayList<>();
        for (JsonNode node : content.get("changes")) {
            // FIXME: The type of change is not actually generated in the prompts yet. We specify the default value to compensate for this in the meantime.
            var type = switch (node.get("type").asText("modify")) {
                case "modify" -> IrisCodeEditorWebsocketService.FileChangeType.MODIFY;
                case "create" -> IrisCodeEditorWebsocketService.FileChangeType.CREATE;
                case "delete" -> IrisCodeEditorWebsocketService.FileChangeType.DELETE;
                case "rename" -> IrisCodeEditorWebsocketService.FileChangeType.RENAME;
                default -> throw new IrisParseResponseException(new Throwable("Unknown exercise change type"));
            };
            var file = node.get("file").asText();
            if (component != ExerciseComponent.PROBLEM_STATEMENT && file.trim().isEmpty()) {
                // This is a special case when the LLM decided to stop generating changes for a component.
                // Ideally, this should not need to be handled here. The only reason it needs to be is because of
                // a bug with Guidance geneach that compels us to use a workaround to break from the loop manually
                // in the guidance program (see https://github.com/guidance-ai/guidance/issues/385).
                // Anyway, if the file is empty, we can just break from the loop.
                break;
            }
            var original = node.get("original").asText();
            if (component == ExerciseComponent.PROBLEM_STATEMENT && original.trim().equals("!done!")) {
                // This is kind of hacky, but necessary for the same reason as above.
                // In the case of the problem statement there is no file generated, so we need another way
                // for the LLM to tell us that it is done generating changes. In this case, it will send
                // the string "!done!" as the original value, which indicates that we should break from the loop.
                // Both of these workarounds should be removed once the bug in Guidance is fixed!
                break;
            }
            var updated = node.get("updated").asText();
            changes.add(new IrisCodeEditorWebsocketService.FileChange(type, file, original, updated));
        }
        return changes;
    }

}
