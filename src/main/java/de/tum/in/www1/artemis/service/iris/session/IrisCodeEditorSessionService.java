package de.tum.in.www1.artemis.service.iris.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
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
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
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

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, AuthorizationCheckService authCheckService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository, GitService gitService, RepositoryService repositoryService,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.authCheckService = authCheckService;
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.templateParticipationRepository = templateParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
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
     * @param session The code editor session to send the request for with all messages and message contents loaded
     */
    public void converseWithModel(IrisCodeEditorSession session) {

        var params = initializeParams(session.getExercise());
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
    public void requestChangesToExerciseComponent(IrisCodeEditorSession session, IrisExercisePlanStep exerciseStep) {
        var component = exerciseStep.getComponent();
        String template = switch (component) {
            case PROBLEM_STATEMENT -> IrisConstants.CODE_EDITOR_ADAPT_PROBLEM_STATEMENT;
            case SOLUTION_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_SOLUTION_REPOSITORY;
            case TEMPLATE_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_TEMPLATE_REPOSITORY;
            case TEST_REPOSITORY -> IrisConstants.CODE_EDITOR_ADAPT_TEST_REPOSITORY;
        };

        var params = initializeParams(session.getExercise());
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
                    if (changes.isEmpty()) {
                        log.error("No changes for exercise " + component + " in response from Iris model");
                    }
                    else {
                        String updatedProblemStatement = null;
                        switch (component) {
                            case PROBLEM_STATEMENT -> updatedProblemStatement = injectChangesIntoProblemStatement(session.getExercise(), changes);
                            case SOLUTION_REPOSITORY -> injectChangesIntoRepository(solutionRepository(session.getExercise()), changes);
                            case TEMPLATE_REPOSITORY -> injectChangesIntoRepository(templateRepository(session.getExercise()), changes);
                            case TEST_REPOSITORY -> injectChangesIntoRepository(testRepository(session.getExercise()), changes);
                        }
                        irisCodeEditorWebsocketService.notifySuccess(session, exerciseStep, updatedProblemStatement);
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
     * Initializes the parameters for the request to Iris. This method merges the unsaved changes from the code editor
     * with the database version of the exercise, and saves the result in a map to send to Iris.
     *
     * @param exercise The programming exercise
     * @return A modifiable map with the parameters for the request to Iris
     */
    private Map<String, Object> initializeParams(ProgrammingExercise exercise) {
        var params = new HashMap<String, Object>();
        params.put("problemStatement", exercise.getProblemStatement());
        params.put("solutionRepository", filterFiles(read(solutionRepository(exercise))));
        params.put("templateRepository", filterFiles(read(templateRepository(exercise))));
        params.put("testRepository", filterFiles(read(testRepository(exercise))));
        return params;
    }

    private Repository solutionRepository(ProgrammingExercise exercise) {
        return solutionParticipationRepository.findByProgrammingExerciseId(exercise.getId()).map(SolutionProgrammingExerciseParticipation::getVcsRepositoryUrl)
                .map(this::repositoryAt).orElseThrow();
    }

    private Repository templateRepository(ProgrammingExercise exercise) {
        return templateParticipationRepository.findByProgrammingExerciseId(exercise.getId()).map(TemplateProgrammingExerciseParticipation::getVcsRepositoryUrl)
                .map(this::repositoryAt).orElseThrow();
    }

    private Repository testRepository(ProgrammingExercise exercise) {
        return Optional.ofNullable(exercise.getVcsTestRepositoryUrl()).map(this::repositoryAt).orElseThrow();
    }

    private Repository repositoryAt(VcsRepositoryUrl url) {
        try {
            return gitService.getOrCheckoutRepository(url, true);
        }
        catch (GitAPIException e) {
            log.error("Could not get or checkout exercise repository", e);
            return null;
        }
    }

    private Map<String, String> read(Repository repository) {
        return new HashMap<>(repositoryService.getFilesWithContent(repository));
    }

    /**
     * There are a few files that we do not want to send to Iris because they are bulky, not representable in plain
     * text, or generally unrelated to the exercise content. This method filters out those files.
     *
     * @param repository The repository to filter
     * @return The filtered repository
     */
    private Map<String, String> filterFiles(Map<String, String> repository) {
        repository.remove("readme.md");
        repository.remove(".gitignore");
        repository.remove(".gitattributes");
        repository.remove("gradlew");
        repository.remove("gradlew.bat");
        repository.entrySet().removeIf(entry -> entry.getKey().startsWith("gradle/wrapper"));
        return repository;
    }

    private enum FileChangeType {
        MODIFY,
        // Add more types here in the future
    }

    private record FileChange(FileChangeType type, String file, String original, String updated) {
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
    private static List<FileChange> extractChangesForComponent(JsonNode content, ExerciseComponent component) throws IrisParseResponseException {
        if (!content.get("changes").isArray()) {
            throw new IrisParseResponseException(new Throwable("Array of exercise changes was not present in response"));
        }
        List<FileChange> changes = new ArrayList<>();
        for (JsonNode node : content.get("changes")) {
            // We will support different file change types in the future. For now, every file change has type MODIFY
            var type = FileChangeType.MODIFY;
            var file = node.get("file").asText("");
            if (component != ExerciseComponent.PROBLEM_STATEMENT && file.trim().equals("!done!")) {
                // This is a special case when the LLM decided to stop generating changes for a component.
                // Ideally, this should not need to be handled here. The only reason it needs to be is because of
                // a bug with Guidance geneach that compels us to use a workaround to break from the loop manually
                // in the guidance program (see https://github.com/guidance-ai/guidance/issues/385).
                // Anyway, if the file is "!done!", we can just break from the loop.
                break;
            }
            var original = node.get("original").asText();
            if (component == ExerciseComponent.PROBLEM_STATEMENT && original.trim().equals("!done!")) {
                // Same issue as above.
                // In the case of the problem statement there is no file generated, so we ask it to
                // respond with "!done!" as the value of the variable "original" if it has no more edits.
                // Both of these workarounds should be removed once the bug in Guidance is fixed!
                break;
            }
            var updated = node.get("updated").asText();
            changes.add(new FileChange(type, file, original, updated));
        }
        return changes;
    }

    private String injectChangesIntoProblemStatement(ProgrammingExercise exercise, List<FileChange> changes) {
        var problemStatement = exercise.getProblemStatement();
        for (FileChange change : changes) {
            problemStatement = problemStatement.replaceFirst(change.original(), change.updated());
        }
        return problemStatement;
    }

    private void injectChangesIntoRepository(Repository repository, List<FileChange> changes) {
        Map<String, Optional<File>> targetedFiles = changes.stream().collect(Collectors.toMap(FileChange::file, change -> gitService.getFileByName(repository, change.file())));
        for (FileChange change : changes) {
            Optional<File> requestedFile = targetedFiles.get(change.file());
            switch (change.type()) {
                case MODIFY -> {
                    if (requestedFile.isPresent()) {
                        try {
                            replaceInFile(requestedFile.get(), change.original(), change.updated());
                        }
                        catch (IOException e) {
                            log.error("Could not modify file " + change.file() + " in repository " + repository, e);
                        }
                    }
                    else {
                        log.info("Iris requested that file " + change.file() + " be modified, but it does not exist in the repository");
                    }
                }
            }
        }
    }

    private void replaceInFile(File file, String original, String updated) throws IOException {
        String currentContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        String newContents = currentContents.replaceFirst(original, updated);
        FileUtils.writeStringToFile(file, newContents, StandardCharsets.UTF_8);
    }

}
