package de.tum.in.www1.artemis.service.iris.session;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import de.tum.in.www1.artemis.repository.iris.IrisExercisePlanStepRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.websocket.IrisCodeEditorWebsocketService;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
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

    private final IrisExercisePlanStepRepository irisExercisePlanStepRepository;

    private final VersionControlService versionControlService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, AuthorizationCheckService authCheckService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository, IrisExercisePlanStepRepository irisExercisePlanStepRepository,
            VersionControlService versionControlService, GitService gitService, RepositoryService repositoryService,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.authCheckService = authCheckService;
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
        this.irisExercisePlanStepRepository = irisExercisePlanStepRepository;
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
     * Loads the content of the file from the file system in the src/main/resources/iris directory.
     * ONLY FOR TESTING PURPOSES!
     *
     * @param fileName The name of the file to load
     * @return The content of the file
     */
    private static String load(String fileName) {
        Path path = Path.of("src", "main", "resources", "iris", fileName);
        try {
            return FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8).trim();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        irisConnectorService.sendRequestV2(load("conversation.hbs"), load("model.txt"), params).handleAsync((response, err) -> {
            if (err != null) {
                log.error("Error while getting response from Iris model", err);
                irisCodeEditorWebsocketService.sendException(session, err.getCause());
                return null;
            }
            if (response == null || !response.content().hasNonNull("response")) {
                log.error("No response from Iris model: " + response);
                irisCodeEditorWebsocketService.sendException(session, new IrisNoResponseException());
                return null;
            }
            log.info("\n\n\nReceived response from iris model: " + response.content().toPrettyString());
            try {
                var irisMessage = toIrisMessage(response.content());
                var saved = irisMessageService.saveMessage(irisMessage, session, IrisMessageSender.LLM);
                irisCodeEditorWebsocketService.sendMessage(saved);
            }
            catch (IrisParseResponseException e) {
                log.error("Error while parsing response from Iris model", e);
                irisCodeEditorWebsocketService.sendException(session, e);
            }
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
    private IrisMessage toIrisMessage(JsonNode content) throws IrisParseResponseException {
        var message = new IrisMessage();
        try {
            var chatWindowResponse = content.required("response").asText();
            message.addContent(new IrisTextMessageContent(message, chatWindowResponse));
        }
        catch (IllegalArgumentException e) {
            log.error("Missing fields, could not parse IrisTextMessageContent: " + content.toPrettyString(), e);
            throw new IrisParseResponseException("Iris response does not have the correct structure");
        }

        if (content.path("steps").isArray()) {
            message.addContent(toExercisePlan(content));
        }

        return message;
    }

    /**
     * Converts a JsonNode into an IrisExercisePlanMessageContent.
     * In order for this to succeed, the JsonNode must have the following structure:
     *
     * <pre>
     *     {
     *          "steps": [
     *              {
     *                  "component": "problem statement"|"solution"|"template"|"tests",
     *                  "instructions": "..."
     *              },
     *              ...
     *          ]
     *     }
     * </pre>
     *
     * @param content The JsonNode to convert
     * @return The converted IrisExercisePlanMessageContent
     * @throws IrisParseResponseException If the JsonNode does not have the correct structure
     */
    private IrisExercisePlan toExercisePlan(JsonNode content) throws IrisParseResponseException {
        var exercisePlan = new IrisExercisePlan();
        List<IrisExercisePlanStep> planSteps = new ArrayList<>();
        for (JsonNode node : content.get("steps")) {
            try {
                ExerciseComponent component = switch (node.required("component").asText()) {
                    // The model is instructed to respond with one of these strings or !done! to indicate that it is done
                    // The model might also misbehave and send something else, in which case we will ignore it
                    case "problem statement" -> ExerciseComponent.PROBLEM_STATEMENT;
                    case "solution" -> ExerciseComponent.SOLUTION_REPOSITORY;
                    case "template" -> ExerciseComponent.TEMPLATE_REPOSITORY;
                    case "tests" -> ExerciseComponent.TEST_REPOSITORY;
                    default -> null;
                };
                if (component == null) {
                    continue;
                }
                var instructions = node.required("instructions").asText();
                planSteps.add(new IrisExercisePlanStep(exercisePlan, component, instructions));
            }
            catch (IllegalArgumentException e) {
                log.error("Missing fields, could not parse IrisExercisePlanStep: " + node.toPrettyString(), e);
            }
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
        irisExercisePlanStepRepository.setInProgress(exerciseStep);
        var component = exerciseStep.getComponent();
        String template = switch (component) {
            case PROBLEM_STATEMENT -> load("problem_statement.hbs");
            case SOLUTION_REPOSITORY -> load("solution_repository.hbs");
            case TEMPLATE_REPOSITORY -> load("template_repository.hbs");
            case TEST_REPOSITORY -> load("test_repository.hbs");
        };

        var exercise = session.getExercise();
        var params = initializeParams(exercise);
        // Add the instructions previously generated by Iris for this step of the plan
        params.put("instructions", exerciseStep.getInstructions());

        irisConnectorService.sendRequestV2(template, load("model.txt"), params).handleAsync((response, err) -> {
            if (err != null) {
                log.error("Error while getting response from Iris model", err);
                irisExercisePlanStepRepository.setFailed(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepException(session, exerciseStep, err.getCause());
                return null;
            }
            if (response == null) {
                log.error("No response from Iris model: " + response);
                irisExercisePlanStepRepository.setFailed(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepException(session, exerciseStep, new IrisNoResponseException());
                return null;
            }
            log.info("Received response from iris model: " + response.content().toPrettyString());
            try {
                String updatedProblemStatement = null;
                Set<String> paths = null;
                if (component == ExerciseComponent.PROBLEM_STATEMENT) {
                    var changes = extractProblemStatementChanges(response.content());
                    log.info("Extracted problem statement changes: " + changes);
                    updatedProblemStatement = injectChangesIntoProblemStatement(exercise, changes);
                }
                else {
                    var changes = extractFileChanges(response.content());
                    log.info("Extracted file changes for exercise " + component + ": " + changes);
                    try (Repository repository = repositoryFor(exercise, component)) {
                        paths = injectChangesIntoRepository(repository, changes);
                    }
                }
                log.info("Setting exercise step as executed");
                irisExercisePlanStepRepository.setCompleted(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepSuccess(session, exerciseStep, paths, updatedProblemStatement);
            }
            catch (IrisParseResponseException e) {
                log.error(e.getMessage(), e);
                irisExercisePlanStepRepository.setFailed(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepException(session, exerciseStep, e);
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
        try (Repository solutionRepository = solutionRepository(exercise);
                Repository templateRepository = templateRepository(exercise);
                Repository testRepository = testRepository(exercise)) {
            params.put("solutionRepository", filterFiles(read(solutionRepository)));
            params.put("templateRepository", filterFiles(read(templateRepository)));
            params.put("testRepository", filterFiles(read(testRepository)));
        }
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

    private Repository repositoryFor(ProgrammingExercise exercise, ExerciseComponent component) {
        return switch (component) {
            case PROBLEM_STATEMENT -> throw new IllegalArgumentException("Cannot get repository for problem statement");
            case SOLUTION_REPOSITORY -> solutionRepository(exercise);
            case TEMPLATE_REPOSITORY -> templateRepository(exercise);
            case TEST_REPOSITORY -> testRepository(exercise);
        };
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
        repository.entrySet().removeIf(entry -> entry.getKey().contains("gradle/wrapper"));
        return repository;
    }

    /**
     * Extracts the problem statement changes from the response of the LLM.
     * The response must have one of the following structures:
     *
     * <pre>
     *     {
     *         "type": "modify",
     *         "changes": [
     *             {
     *                 "from": "start of quote to replace (inclusive)",
     *                 "to": "end of quote to replace (exclusive)",
     *                 "updated": "updated content to replace the quote with"
     *             },
     *             ...
     *         ]
     *     }
     * </pre>
     *
     * or
     *
     * <pre>
     *     {
     *         "type": "overwrite",
     *         "updated": "new problem statement"
     *     }
     * </pre>
     *
     * @param content The JsonNode to extract the problem statement changes from
     * @return The extracted problem statement changes
     * @throws IrisParseResponseException If the JsonNode does not have the correct structure
     */
    private List<ProblemStatementChange> extractProblemStatementChanges(JsonNode content) throws IrisParseResponseException {
        List<ProblemStatementChange> changes = new ArrayList<>();
        try {
            var type = content.required("type").asText();
            switch (type) {
                case "overwrite" -> changes.add(ProblemStatementOverwrite.parse(content));
                case "modify" -> {
                    if (!content.path("changes").isArray()) {
                        log.error("Missing fields, could not parse ProblemStatementChange: " + content.toPrettyString());
                        break;
                    }
                    for (JsonNode node : content.required("changes")) {
                        try {
                            if (node.required("from").asText().equals("!done!")) {
                                // This is a special case when the LLM decides to stop generating changes.
                                // It means that the previous change was the final one, and we should stop parsing the response.
                                // Ideally, this last iteration should not even happen.
                                // The only reason it needs to is because of a bug with Guidance that compels us to
                                // use a workaround to break from the #geneach loop manually
                                // (see https://github.com/guidance-ai/guidance/issues/385).
                                break;
                            }
                            changes.add(ProblemStatementReplacement.parse(node));
                        }
                        catch (IllegalArgumentException e) {
                            log.error("Missing fields, could not parse ProblemStatementReplacement: " + node.toPrettyString(), e);
                        }
                    }
                }
            }
        }
        catch (IllegalArgumentException e) {
            log.error("Missing fields, could not parse ProblemStatementChange: " + content.toPrettyString(), e);
        }
        if (changes.isEmpty()) {
            throw new IrisParseResponseException("Was not able to parse any changes");
        }
        return changes;
    }

    /**
     * Extracts the changes for a specific component from the response of the LLM.
     * The response must have the following structure:
     *
     * <pre>
     *     {
     *         "changes": [
     *             {
     *                 "type": "modify|overwrite|create|delete|rename",
     *                 "file": "path/to/file",
     *                 --other fields depending on the specific type of change--
     *             },
     *             ...
     *         ]
     *     }
     * </pre>
     *
     * If the type of change is unrecognized, it will be ignored.
     * This conveniently also allows us to ignore the final "!done!" change that Iris sends.
     *
     * @param content The JsonNode to extract the changes from
     * @return The extracted changes
     * @throws IrisParseResponseException If the JsonNode does not have the correct structure
     */
    private List<FileChange> extractFileChanges(JsonNode content) throws IrisParseResponseException {
        if (!content.path("changes").isArray()) {
            throw new IrisParseResponseException("Could not parse file changes: " + content.toPrettyString());
        }
        List<FileChange> changes = new ArrayList<>();
        for (JsonNode node : content.path("changes")) {
            try {
                var fileChange = switch (node.path("type").asText()) {
                    case "overwrite" -> OverwriteFileChange.parse(node);
                    case "modify" -> ModifyFileChange.parse(node);
                    case "create" -> CreateFileChange.parse(node);
                    case "delete" -> DeleteFileChange.parse(node);
                    case "rename" -> RenameFileChange.parse(node);
                    default -> null;
                };
                if (fileChange != null) {
                    changes.add(fileChange);
                }
            }
            catch (IllegalArgumentException e) {
                log.error("Missing fields, could not parse FileChange: " + node.toPrettyString(), e);
            }
        }
        if (changes.isEmpty()) {
            throw new IrisParseResponseException("Was not able to parse any changes");
        }
        return changes;
    }

    /**
     * An exception that occurs when Iris generates a change that cannot be applied for some reason.
     */
    private static class IrisChangeException extends Exception {

        public IrisChangeException(String message) {
            super(message);
        }
    }

    /**
     * Iris can generate different kinds of changes to files.
     * This interface represents a change that can be applied to a file.
     */
    private interface FileChange {

        /**
         * Returns the path of the file that this change should be applied to.
         *
         * @return The path of the file
         */
        String path();

        /**
         * Applies the change to the provided Optional<File>. Some change types expect the file to exist, while others
         * do not. If the file does not exist, the Optional will be empty.
         *
         * @param file              The file to apply the change to
         * @param repositoryService The repository service to use for applying the change
         * @param repository        The repository to apply the change to
         * @throws IOException         If an I/O error occurs
         * @throws IrisChangeException If the change cannot be applied because of a mistake made by Iris
         */
        void apply(Optional<File> file, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException;
    }

    /**
     * A file change that modifies the contents of a file by performing a find-and-replace operation.
     *
     * @param path     The path of the file to modify
     * @param original The original contents of the file (to replace)
     * @param updated  The updated contents of the file
     */
    private record ModifyFileChange(String path, String original, String updated) implements FileChange {

        /**
         * Replaces the first occurrence of the original string with the updated string in the given file.
         *
         * @throws IOException If the file could not be read or written to
         */
        @Override
        public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
            if (requestedFile.isEmpty()) {
                throw new IrisChangeException("Could not modify file '" + path + "' because it does not exist");
            }
            String currentContent = FileUtils.readFileToString(requestedFile.get(), StandardCharsets.UTF_8);
            // We only want to replace the first occurrence of the original string (for now)
            // String.replaceFirst() uses regex, so we need to escape the original string with Pattern.quote()
            // Matcher.quoteReplacement() escapes the updated string so that it can be used as a replacement
            String newContents = currentContent.replaceFirst(Pattern.quote(original), Matcher.quoteReplacement(updated));
            FileUtils.writeStringToFile(requestedFile.get(), newContents, StandardCharsets.UTF_8);
        }

        /**
         * Parses a JsonNode into a ModifyFileChange.
         * The JsonNode must have strings for the fields "path", "original", and "updated".
         *
         * @param node The JsonNode to parse
         * @return The parsed ModifyFileChange
         * @throws IllegalArgumentException If the JsonNode does not have the correct structure
         */
        static FileChange parse(JsonNode node) throws IllegalArgumentException {
            String path = node.required("path").asText();
            String original = node.required("original").asText();
            String updated = node.required("updated").asText();
            return new ModifyFileChange(path, original, updated);
        }
    }

    /**
     * A file change that overwrites the contents of a file with the given contents.
     *
     * @param path    The path of the file to overwrite
     * @param updated The new contents of the file
     */
    private record OverwriteFileChange(String path, String updated) implements FileChange {

        @Override
        public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
            if (requestedFile.isEmpty()) {
                throw new IrisChangeException("Could not overwrite file '" + path + "' because it does not exist");
            }
            FileUtils.writeStringToFile(requestedFile.get(), updated, StandardCharsets.UTF_8);
        }

        /**
         * Parses a JsonNode into an OverwriteFileChange.
         * The JsonNode must have strings for the fields "path" and "content".
         *
         * @param node The JsonNode to parse
         * @return The parsed OverwriteFileChange
         * @throws IllegalArgumentException If the JsonNode does not have the correct structure
         */
        static FileChange parse(JsonNode node) throws IllegalArgumentException {
            String path = node.required("path").asText();
            String updated = node.required("updated").asText();
            return new OverwriteFileChange(path, updated);
        }
    }

    /**
     * A file change that creates a new file with the given contents.
     *
     * @param path    The path of the file to create
     * @param content The contents of the file to create
     */
    private record CreateFileChange(String path, String content) implements FileChange {

        @Override
        public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
            if (requestedFile.isPresent()) {
                throw new IrisChangeException("Could not create file '" + path + "' because it already exists");
            }
            repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }

        /**
         * Parses a JsonNode into a CreateFileChange.
         * The JsonNode must have strings for the fields "path" and "content".
         *
         * @param node The JsonNode to parse
         * @return The parsed CreateFileChange
         * @throws IllegalArgumentException If the JsonNode does not have the correct structure
         */
        static FileChange parse(JsonNode node) throws IllegalArgumentException {
            String path = node.required("path").asText();
            String content = node.required("content").asText();
            return new CreateFileChange(path, content);
        }
    }

    /**
     * A file change that deletes a file.
     *
     * @param path The path of the file to delete
     */
    private record DeleteFileChange(String path) implements FileChange {

        @Override
        public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
            if (requestedFile.isEmpty()) {
                throw new IrisChangeException("Could not delete file '" + path + "' because it does not exist");
            }
            repositoryService.deleteFile(repository, path);
        }

        /**
         * Parses a JsonNode into a DeleteFileChange.
         * The JsonNode must have a string for the field "path".
         *
         * @param node The JsonNode to parse
         * @return The parsed DeleteFileChange
         * @throws IllegalArgumentException If the JsonNode does not have the correct structure
         */
        static FileChange parse(JsonNode node) throws IllegalArgumentException {
            String path = node.required("path").asText();
            return new DeleteFileChange(path);
        }
    }

    /**
     * A file change that renames a file.
     *
     * @param path    The path of the file to rename
     * @param newPath The new path of the file
     */
    private record RenameFileChange(String path, String newPath) implements FileChange {

        @Override
        public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
            if (requestedFile.isEmpty()) {
                throw new IrisChangeException("Could not rename file '" + path + "' because it does not exist");
            }
            repositoryService.renameFile(repository, new FileMove(path, newPath));
        }

        /**
         * Parses a JsonNode into a RenameFileChange.
         * The JsonNode must have strings for the fields "path" and "newPath".
         *
         * @param node The JsonNode to parse
         * @return The parsed RenameFileChange
         * @throws IllegalArgumentException If the JsonNode does not have the correct structure
         */
        static FileChange parse(JsonNode node) throws IllegalArgumentException {
            String path = node.required("path").asText();
            String newPath = node.required("newPath").asText();
            return new RenameFileChange(path, newPath);
        }
    }

    /**
     * A change that can be applied to the problem statement of an exercise.
     */
    private interface ProblemStatementChange {

        String apply(String problemStatement) throws IrisChangeException;
    }

    /**
     * A change that replaces a range of text in the problem statement with the updated string.
     *
     * @param from    The start of the range to replace, inclusive
     * @param to      The end of the range to replace, exclusive
     * @param updated The updated string to replace the range with
     */
    private record ProblemStatementReplacement(String from, String to, String updated) implements ProblemStatementChange {

        @Override
        public String apply(String problemStatement) throws IrisChangeException {
            String before;
            int startIndex;
            if (from.equals("!start!")) {
                before = "";
                startIndex = 0;
            }
            else {
                // Search for the start string in the problem statement
                startIndex = problemStatement.indexOf(from);
                if (startIndex == -1) {
                    throw new IrisChangeException("Could not locate range start '" + from + "'");
                }
                before = problemStatement.substring(0, startIndex);
            }

            String after;
            if (to.equals("!end!")) {
                after = "";
            }
            else {
                // Search for the end string in the remaining string
                int endIndex = problemStatement.substring(startIndex).indexOf(to);
                if (endIndex == -1) {
                    throw new IrisChangeException("Could not find range end '" + to + "' after range start '" + from + "'");
                }
                endIndex += startIndex; // Add the start index to get the index in the original problem statement
                after = problemStatement.substring(endIndex);
            }

            // Replace the range with the updated string
            return before + updated + after;
        }

        static ProblemStatementChange parse(JsonNode node) throws IllegalArgumentException {
            String from = node.required("from").asText();
            String to = node.required("to").asText();
            String updated = node.required("updated").asText();
            return new ProblemStatementReplacement(from, to, updated);
        }
    }

    /**
     * A change that overwrites the entire problem statement of an exercise.
     *
     * @param updated The new problem statement
     */
    private record ProblemStatementOverwrite(String updated) implements ProblemStatementChange {

        @Override
        public String apply(String problemStatement) {
            return updated;
        }

        static ProblemStatementChange parse(JsonNode node) throws IllegalArgumentException {
            String updated = node.required("updated").asText();
            return new ProblemStatementOverwrite(updated);
        }
    }

    /**
     * Injects the changes into the problem statement of the exercise. This method replaces the first occurrence of each
     * original string with the corresponding updated string in the problem statement.
     *
     * @param exercise The programming exercise
     * @param changes  The changes to inject
     * @return The updated problem statement
     */
    private String injectChangesIntoProblemStatement(ProgrammingExercise exercise, List<ProblemStatementChange> changes) {
        log.info("Injecting changes into problem statement: \n\n\n" + changes);
        var problemStatement = exercise.getProblemStatement();
        int successes = 0;
        int failures = 0;
        for (ProblemStatementChange change : changes) {
            try {
                // Replace the range with the updated string
                problemStatement = change.apply(problemStatement);
                successes++;
            }
            catch (IrisChangeException e) {
                log.info(e.getMessage());
                failures++;
            }
        }
        log.info("Successfully applied " + successes + " changes to problem statement, " + failures + " changes failed");
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
        log.info("Injecting changes into repository: \n\n\n" + changes);
        Map<String, Optional<File>> targetedFiles = changes.stream().map(FileChange::path).distinct()
                .collect(Collectors.toMap(fileName -> fileName, fileName -> gitService.getFileByName(repository, fileName)));
        Set<String> pathsToUpdate = new HashSet<>();
        int successes = 0;
        int failures = 0;
        for (FileChange change : changes) {
            Optional<File> requestedFile = targetedFiles.get(change.path());
            try {
                change.apply(requestedFile, repositoryService, repository);
                pathsToUpdate.add(change.path());
                successes++;
            }
            catch (IOException e) {
                log.error("Encountered an IOException while applying change: " + change, e);
            }
            catch (IrisChangeException e) {
                log.info(e.getMessage());
                failures++;
            }
        }
        log.info("Successfully applied " + successes + " changes to repository, " + failures + " changes failed");
        return pathsToUpdate;
    }

}
