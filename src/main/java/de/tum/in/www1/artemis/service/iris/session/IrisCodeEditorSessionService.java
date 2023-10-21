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
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
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
import de.tum.in.www1.artemis.web.rest.dto.iris.UnsavedCodeEditorChangesDTO;
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

    private final IrisCodeEditorSessionRepository irisCodeEditorSessionRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, AuthorizationCheckService authCheckService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository, IrisSessionRepository irisSessionRepository, GitService gitService,
            RepositoryService repositoryService) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.authCheckService = authCheckService;
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
        this.irisSessionRepository = irisSessionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    public IrisCodeEditorSession createSession(ProgrammingExercise exercise, User user) {
        return irisCodeEditorSessionRepository.save(new IrisCodeEditorSession(exercise, user));
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
        // Code editor sessions should probably be available for every programming exercise, especially brand-new ones
        // Might want to check something here in the future. For now, do nothing.
    }

    /**
     * Sends a request containing the current state of the exercise repositories in the code editor and the entire
     * conversation history to the LLM, and handles the response.
     *
     * @param irisSession The session to send the request for
     */
    public void converseWithModel(IrisSession irisSession, UnsavedCodeEditorChangesDTO unsavedChanges) {
        var fromDB = irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        if (!(fromDB instanceof IrisCodeEditorSession session)) {
            throw new BadRequestException("Iris session is not a code editor session");
        }

        var params = initializeParams(session.getExercise(), unsavedChanges);
        params.put("chatHistory", session.getMessages()); // Additionally add the chat history to the request

        // The template and model are hard-coded for now, but will be configurable in the future
        irisConnectorService.sendRequestV2(IrisConstants.CODE_EDITOR_CONVERSATION, "STRATEGY_GPT35_TURBO", params).thenAcceptAsync(response -> {
            if (response == null) {
                log.error("No response from Iris model");
                irisCodeEditorWebsocketService.sendException(session, new IrisNoResponseException());
                return;
            }
            log.info("Received conversation response from Iris model");
            try {
                log.info(response.content().toPrettyString());
                var irisMessage = toIrisMessage(response.content());
                var saved = irisMessageService.saveMessage(irisMessage, session, IrisMessageSender.LLM);
                irisCodeEditorWebsocketService.sendMessage(saved);
            }
            catch (IrisParseResponseException e) {
                log.error("Error while parsing response from Iris model", e);
                irisCodeEditorWebsocketService.sendException(session, e);
            }
        }).exceptionallyAsync(err -> {
            log.error("Error while getting response from Iris model", err);
            irisCodeEditorWebsocketService.sendException(session, err.getCause());
            return null;
        });
    }

    private static IrisMessage toIrisMessage(JsonNode content) throws IrisParseResponseException {
        var message = new IrisMessage();
        if (!content.hasNonNull("response")) {
            throw new IrisParseResponseException(new Throwable("No response"));
        }
        var chatWindowResponse = content.get("response").asText();
        message.addContent(new IrisTextMessageContent(message, chatWindowResponse));

        if (content.hasNonNull("components")) {
            message.addContent(toExercisePlan(content));
        }

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
        List<IrisExercisePlanStep> planSteps = new ArrayList<>();
        for (JsonNode node : content.get("components")) {
            if (!node.hasNonNull("plan")) {
                continue; // This might happen as a result of the LLM deciding to stop generating components, which is OK
            }
            var plan = node.get("plan").asText();
            ExerciseComponent component = switch (node.get("component").asText("")) {
                // The model is instructed to respond with one of these strings, but it might misbehave and send something else
                case "problem statement" -> ExerciseComponent.PROBLEM_STATEMENT;
                case "solution" -> ExerciseComponent.SOLUTION_REPOSITORY;
                case "template" -> ExerciseComponent.TEMPLATE_REPOSITORY;
                case "tests" -> ExerciseComponent.TEST_REPOSITORY;
                default -> null; // No idea what the model responded with, so we just ignore it
            };
            if (component != null) {
                planSteps.add(new IrisExercisePlanStep(exercisePlan, component, plan));
            }
        }
        if (planSteps.isEmpty()) {
            throw new IrisParseResponseException(new Throwable("No exercise plan components"));
        }
        exercisePlan.setSteps(planSteps);
        return exercisePlan;
    }

    /**
     * Requests exercise changes from the Iris model for the given session and exercise plan. This method sends a
     * request to the Iris model for each component in the exercise plan, and handles the response to extract the
     * changes and send them to the websocket service.
     *
     * @param session      The IrisCodeEditorSession to request exercise changes for
     * @param exerciseStep The IrisExercisePlanComponent to request exercise changes for
     */
    public void requestChangesToExerciseComponent(IrisCodeEditorSession session, IrisExercisePlanStep exerciseStep, UnsavedCodeEditorChangesDTO unsavedChanges) {
        var component = exerciseStep.getComponent();
        String template = switch (component) {
            case PROBLEM_STATEMENT -> IrisConstants.CODE_EDITOR_ADAPT_PROBLEM_STATEMENT;
            case SOLUTION_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_SOLUTION_REPOSITORY;
            case TEMPLATE_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_TEMPLATE_REPOSITORY;
            case TEST_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_TEST_REPOSITORY;
        };

        var params = initializeParams(session.getExercise(), unsavedChanges);
        // Add the instructions previously generated by Iris for this step of the plan
        params.put("instructions", exerciseStep.getInstructions());

        irisConnectorService.sendRequestV2(template, "STRATEGY_GPT35_TURBO", params).handleAsync((response, err) -> {
            if (err != null) {
                log.error("Error while getting response from Iris model", err);
                irisCodeEditorWebsocketService.sendException(session, err.getCause());
            }
            else if (response == null) {
                log.error("No response from Iris model");
                irisCodeEditorWebsocketService.sendException(session, new IrisNoResponseException());
            }
            else {
                log.info("Received response containing changes to exercise " + component + " from Iris model");
                try {
                    var changes = extractChangesForComponent(response.content(), component);
                    if (!changes.isEmpty()) {
                        // In this case we do not save anything, as these changes must first be approved by the user
                        irisCodeEditorWebsocketService.sendChanges(session, component, changes);
                    }
                    else {
                        log.error("No changes for exercise " + component + " in response from Iris model");
                    }
                }
                catch (IrisParseResponseException e) {
                    log.error("Error while parsing exercise changes from Iris model", e);
                    irisCodeEditorWebsocketService.sendException(session, e);
                }
            }
            return null;
        });
    }

    /**
     * Extracts the changes for a specific component from the response of the LLM. The response must have the following
     * structure:
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
            // We will support different file change types in the future. For now, every file change has type MODIFY
            var type = IrisCodeEditorWebsocketService.FileChangeType.MODIFY;
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

    /**
     * Initializes the parameters for the request to Iris.
     * This method merges the unsaved changes from the code editor with the database version of the exercise,
     * and saves the result in a map to send to Iris.
     *
     * @param exercise The programming exercise
     * @param unsaved  The UnsavedCodeEditorChangesDTO object containing any unsaved changes from the code editor
     * @return A modifiable map with the parameters for the request to Iris
     */
    private Map<String, Object> initializeParams(ProgrammingExercise exercise, UnsavedCodeEditorChangesDTO unsaved) {
        var problemStatement = Optional.ofNullable(unsaved.problemStatement()).orElse(exercise.getProblemStatement());
        var solutionRepository = prepareRepository(exercise.getVcsSolutionRepositoryUrl(), unsaved.solutionRepository());
        var templateRepository = prepareRepository(exercise.getVcsTemplateRepositoryUrl(), unsaved.templateRepository());
        var testRepository = prepareRepository(exercise.getVcsTestRepositoryUrl(), unsaved.testRepository());
        var params = new HashMap<String, Object>();
        params.put("problemStatement", problemStatement);
        params.put("solutionRepository", solutionRepository);
        params.put("templateRepository", templateRepository);
        params.put("testRepository", testRepository);
        return params;
    }

    /**
     * Prepares a repository for sending to the Iris model. This method loads the repository from the database if
     * possible, and merges it with any unsaved changes. It then filters out any unwanted files.
     *
     * @param vcsRepositoryUrl The URL of the repository to prepare
     * @param unsavedChanges   Any unsaved changes to merge with the repository
     * @return The prepared repository
     */
    private Map<String, String> prepareRepository(@Nullable VcsRepositoryUrl vcsRepositoryUrl, Map<String, String> unsavedChanges) {
        var merged = Optional.ofNullable(vcsRepositoryUrl).map(this::loadRepositoryFromDatabase) // Load from database if possible
                .map(HashMap::new) // Make modifiable
                .map(repository -> merge(repository, unsavedChanges)) // Any unsaved changes take priority
                .orElseGet(() -> new HashMap<>(unsavedChanges)); // If there is no database version, just use the unsaved changes
        return filterOutUnwantedFiles(merged);
    }

    /**
     * Loads the repository under the given URL as a map of file paths to file contents.
     *
     * @param vcsRepositoryUrl The URL of the repository to load
     * @return The loaded repository
     */
    private Map<String, String> loadRepositoryFromDatabase(VcsRepositoryUrl vcsRepositoryUrl) {
        try {
            Repository repository = gitService.getOrCheckoutRepository(vcsRepositoryUrl, true);
            return repositoryService.getFilesWithContent(repository);
        }
        catch (GitAPIException e) {
            throw new InternalServerErrorException("Could not get or checkout exercise repository");
        }
    }

    /**
     * Merge two maps, with the values from the higher priority map taking precedence in case of a key collision.
     *
     * @param lowerPriority  The map with lower priority
     * @param higherPriority The map with higher priority
     * @return The merged map
     */
    private static Map<String, String> merge(Map<String, String> lowerPriority, Map<String, String> higherPriority) {
        Map<String, String> merged = new HashMap<>(lowerPriority);
        merged.putAll(higherPriority);
        return merged;
    }

    /**
     * There are a few files that we do not want to send to Iris because they are bulky, not representable in plain
     * text, or generally unrelated to the exercise content. This method filters out those files.
     *
     * @param repository The repository to filter
     * @return The filtered repository
     */
    private Map<String, String> filterOutUnwantedFiles(Map<String, String> repository) {
        repository.remove("readme.md");
        repository.remove(".gitignore");
        repository.remove(".gitattributes");
        repository.remove("gradlew");
        repository.remove("gradlew.bat");
        repository.entrySet().removeIf(entry -> entry.getKey().startsWith("gradle/wrapper"));
        return repository;
    }

}
