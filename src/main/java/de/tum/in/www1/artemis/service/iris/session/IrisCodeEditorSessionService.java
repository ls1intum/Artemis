package de.tum.in.www1.artemis.service.iris.session;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
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

    private final VersionControlService versionControlService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, AuthorizationCheckService authCheckService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository, VersionControlService versionControlService, GitService gitService,
            RepositoryService repositoryService, TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.authCheckService = authCheckService;
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.templateParticipationRepository = templateParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
    }

    /**
     * Creates a new Code Editor session for the given exercise and user, and saves it in the database.
     *
     * @param exercise The programming exercise
     * @param user     The user
     * @return The created session
     */
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
        // checkHasAccessToIrisSession(castToSessionType(session, IrisCodeEditorSession.class), user);
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

    /**
     * Converts a JsonNode into an IrisMessage.
     * To do this, it checks the JsonNode for a field "response". If it is present, it creates an IrisTextMessageContent
     * with the value of the field as the message content. If the JsonNode also has a field "components", it creates an
     * IrisExercisePlanMessageContent with the parsed value of the field as the message content.
     *
     * @param content The JsonNode to convert
     * @return The converted IrisMessage
     * @throws IrisParseResponseException If the JsonNode does not have the correct structure
     */
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

        var exercise = session.getExercise();
        var params = initializeParams(exercise);
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
                        Set<String> paths = null;
                        switch (component) {
                            case PROBLEM_STATEMENT -> updatedProblemStatement = injectChangesIntoProblemStatement(exercise, changes);
                            case SOLUTION_REPOSITORY -> paths = injectChangesIntoRepository(solutionRepository(exercise), changes);
                            case TEMPLATE_REPOSITORY -> paths = injectChangesIntoRepository(templateRepository(exercise), changes);
                            case TEST_REPOSITORY -> paths = injectChangesIntoRepository(testRepository(exercise), changes);
                        }
                        irisCodeEditorWebsocketService.sendFilesChanged(session, exerciseStep, paths, updatedProblemStatement);
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

    /**
     * Gets the solution repository for a given exercise. This method uses the
     * SolutionProgrammingExerciseParticipationRepository to find the solution participation for the exercise.
     * If the participation is not found, it will throw an exception.
     *
     * @param exercise The exercise to get the solution repository for
     * @return The solution repository
     */
    private Repository solutionRepository(ProgrammingExercise exercise) {
        return solutionParticipationRepository.findByProgrammingExerciseId(exercise.getId()).map(this::repositoryAt).orElseThrow();
    }

    /**
     * Fetches the template repository for a given exercise. This method uses the
     * TemplateProgrammingExerciseParticipationRepository to find the template participation for the exercise.
     * If the participation is not found, it will throw an exception.
     *
     * @param exercise The exercise to get the template repository for
     * @return The template repository
     */
    private Repository templateRepository(ProgrammingExercise exercise) {
        return templateParticipationRepository.findByProgrammingExerciseId(exercise.getId()).map(this::repositoryAt).orElseThrow();
    }

    /**
     * Gets the test repository for a given exercise. This method uses the URL of the test repository that is stored in
     * the exercise. If the URL is null, it will throw an exception.
     *
     * @param exercise The exercise to get the test repository for
     * @return The test repository
     */
    private Repository testRepository(ProgrammingExercise exercise) {
        return Optional.ofNullable(exercise.getVcsTestRepositoryUrl()).map(this::repositoryAt).orElseThrow();
    }

    /**
     * Fetches the repository for a given participation.
     * If the repository is already cached, it will be retrieved from the cache.
     *
     * @param participation The participation to fetch the repository for
     * @return The repository
     */
    private Repository repositoryAt(ProgrammingExerciseParticipation participation) {
        var url = participation.getVcsRepositoryUrl();
        try {
            // This check reduces the amount of REST-calls that retrieve the default branch of a repository.
            // Retrieving the default branch is not necessary if the repository is already cached.
            if (gitService.isRepositoryCached(url)) {
                return gitService.getOrCheckoutRepository(url, true);
            }
            else {
                String branch = versionControlService.getOrRetrieveBranchOfParticipation(participation);
                return gitService.getOrCheckoutRepository(url, true, branch);
            }
        }
        catch (GitAPIException e) {
            log.error("Could not get or checkout exercise repository", e);
            return null;
        }
    }

    /**
     * Fetches the repository for a given URL.
     *
     * @param url The URL to fetch the repository for
     * @return The repository
     */
    private Repository repositoryAt(VcsRepositoryUrl url) {
        try {
            return gitService.getOrCheckoutRepository(url, true);
        }
        catch (GitAPIException e) {
            log.error("Could not get or checkout exercise repository", e);
            return null;
        }
    }

    /**
     * Reads the files in a repository and returns them as a map from file name to file contents.
     *
     * @param repository The repository to read
     * @return The map of file names to file contents
     */
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

    /**
     * Iris can generate different types of changes to files in the exercise repositories. This enum represents the
     * different types of changes that Iris can generate. Currently, only MODIFY is supported.
     * In the future we would like to support more types of changes, such as CREATE, DELETE, and RENAME.
     */
    private enum FileChangeType {
        MODIFY, CREATE,
        // Add more types here in the future
    }

    /**
     * A file change represents a change to a file in an exercise repository. It contains the type of change, the name
     * of the file, and the original and updated contents of the file.
     * How the original and updated contents are used depends on the type of change.
     *
     * @param type     the type of change
     * @param file     the name of the file
     * @param original the original contents of the file
     * @param updated  the updated contents of the file
     */
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
            var type = switch (node.get("type").asText("")) {
                case "modify" -> FileChangeType.MODIFY;
                case "create" -> FileChangeType.CREATE;
                default -> null;
            };
            if (type == null) {
                continue;
            }
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

    /**
     * Injects the changes into the problem statement of the exercise. This method replaces the first occurrence of each
     * original string with the corresponding updated string in the problem statement.
     *
     * @param exercise The programming exercise
     * @param changes  The changes to inject
     * @return The updated problem statement
     */
    private String injectChangesIntoProblemStatement(ProgrammingExercise exercise, List<FileChange> changes) {
        var problemStatement = exercise.getProblemStatement();
        for (FileChange change : changes) {
            problemStatement = problemStatement.replaceFirst(Pattern.quote(change.original()), Matcher.quoteReplacement(change.updated()));
        }
        return problemStatement;
    }

    /**
     * Injects the changes into the repository. This method replaces the first occurrence of each original string with
     * the corresponding updated string in the file with the same name as the file in the change.
     * Returned is a set of paths to the files that were actually modified.
     *
     * @param repository The repository to inject the changes into
     * @param changes    The changes to inject
     */
    private Set<String> injectChangesIntoRepository(Repository repository, List<FileChange> changes) {
        Map<String, Optional<File>> targetedFiles = changes.stream().collect(Collectors.toMap(FileChange::file, change -> gitService.getFileByName(repository, change.file())));
        Set<String> paths = new HashSet<>();
        for (FileChange change : changes) {
            Optional<File> requestedFile = targetedFiles.get(change.file());
            switch (change.type()) {
                case MODIFY -> {
                    if (requestedFile.isPresent()) {
                        try {
                            log.info("trying to replace " + change.original() + " with " + change.updated() + " in " + change.file());
                            replaceInFile(requestedFile.get(), change.original(), change.updated());
                            paths.add(change.file());
                        }
                        catch (IOException e) {
                            log.error("Could not modify file " + change.file() + " in repository " + repository, e);
                        }
                    }
                    else {
                        log.info("Iris requested that file " + change.file() + " be modified, but it does not exist in the repository");
                    }
                }
                case CREATE -> {
                    if (requestedFile.isEmpty()) {
                        try {
                            repositoryService.createFile(repository, change.file(), new ByteArrayInputStream(change.updated().getBytes()));
                        }
                        catch (IOException e) {
                            log.error("File " + change.file() + " already exists");
                        }
                    }

                }
            }
        }
        return paths;
    }

    /**
     * Replaces the first occurrence of the original string with the updated string in the given file.
     *
     * @param file     The file to modify
     * @param original The original string to replace
     * @param updated  The updated string to replace the original string with
     * @throws IOException If the file could not be read or written to
     */
    private void replaceInFile(File file, String original, String updated) throws IOException {
        String currentContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        // We only want to replace the first occurrence of the original string (for now)
        // String.replaceFirst() uses regex, so we need to escape the original string with Pattern.quote()
        // Matcher.quoteReplacement() escapes the updated string so that it can be used as a replacement
        String newContents = currentContents.replaceFirst(Pattern.quote(original), Matcher.quoteReplacement(updated));
        FileUtils.writeStringToFile(file, newContents, StandardCharsets.UTF_8);
    }

}
