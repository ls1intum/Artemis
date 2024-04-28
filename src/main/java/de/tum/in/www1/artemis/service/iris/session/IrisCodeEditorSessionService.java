package de.tum.in.www1.artemis.service.iris.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.iris.message.ExerciseComponent;
import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlan;
import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanStep;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisExercisePlanStepRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.file.CreateFileChange;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.file.DeleteFileChange;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.file.FileChange;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.file.ModifyFileChange;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.file.RenameFileChange;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.problemstatement.ProblemStatementChange;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.problemstatement.ProblemStatementOverwrite;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.problemstatement.ProblemStatementReplacement;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisCodeEditorWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service to handle the code editor subsystem of Iris.
 */
@Service
@Profile("iris")
public class IrisCodeEditorSessionService implements IrisChatBasedFeatureInterface<IrisCodeEditorSession> {

    private static final Logger log = LoggerFactory.getLogger(IrisCodeEditorSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisCodeEditorWebsocketService irisCodeEditorWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisCodeEditorSessionRepository irisCodeEditorSessionRepository;

    private final IrisExercisePlanStepRepository irisExercisePlanStepRepository;

    private final VersionControlService versionControlService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final ObjectMapper objectMapper;

    public IrisCodeEditorSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, AuthorizationCheckService authCheckService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository, IrisExercisePlanStepRepository irisExercisePlanStepRepository,
            VersionControlService versionControlService, GitService gitService, RepositoryService repositoryService,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            IrisSessionRepository irisSessionRepository, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
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
        this.irisSessionRepository = irisSessionRepository;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
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
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisCodeEditorSession session) {
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, session.getExercise(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Code Editor Session", session.getId());
        }
    }

    /**
     * Checks if the feature is active for the context (e.g. an exercise) of the session.
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisCodeEditorSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CODE_EDITOR, session.getExercise());
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param message that should be sent over the websocket
     */
    @Override
    public void sendOverWebsocket(IrisMessage message) {
        irisCodeEditorWebsocketService.sendMessage(message);
    }

    // @formatter:off
    record CodeEditorChatDTO(
            String problemStatement,
            Map<String, String> solutionRepository,
            Map<String, String> templateRepository,
            Map<String, String> testRepository,
            List<IrisMessage> chatHistory
    ) {}
    // @formatter:on

    /**
     * Sends a request containing the current state of the exercise repositories in the code editor and the entire
     * conversation history to the LLM, and handles the response.
     *
     * @param irisSession The code editor session to send the request for with all messages and message contents loaded
     */
    @Override
    public void requestAndHandleResponse(IrisSession irisSession) {
        var sessionFromDB = irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        if (!(sessionFromDB instanceof IrisCodeEditorSession session)) {
            throw new BadRequestException("Iris session is not a code editor session");
        }
        var exercise = session.getExercise();

        // @formatter:off
        var dto = new CodeEditorChatDTO(
                exercise.getProblemStatement(),
                filterFiles(read(solutionRepository(exercise))),
                filterFiles(read(templateRepository(exercise))),
                filterFiles(read(testRepository(exercise))),
                session.getMessages()
        );
        // @formatter:on

        var settings = irisSettingsService.getCombinedIrisSettingsFor(exercise, false).irisCodeEditorSettings();
        irisConnectorService.sendRequestV2(settings.getChatTemplate().getContent(), settings.getPreferredModel(), dto).handleAsync((response, err) -> {
            if (err != null) {
                log.error("Error while getting response from Iris model", err);
                irisCodeEditorWebsocketService.sendException(session, err.getCause());
                return null;
            }
            if (response == null || !response.content().hasNonNull("response")) {
                log.error("No response from Iris model: {}", response);
                irisCodeEditorWebsocketService.sendException(session, new IrisNoResponseException());
                return null;
            }
            log.info("Received response from iris model: {}", response.content().toPrettyString());
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
            message.addContent(new IrisTextMessageContent(chatWindowResponse));
        }
        catch (IllegalArgumentException e) {
            log.error("Missing fields, could not parse IrisTextMessageContent: {}", content.toPrettyString(), e);
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
                planSteps.add(new IrisExercisePlanStep(component, instructions));
            }
            catch (IllegalArgumentException e) {
                log.error("Missing fields, could not parse IrisExercisePlanStep: {}", node.toPrettyString(), e);
            }
        }
        exercisePlan.setSteps(planSteps);
        return exercisePlan;
    }

    // @formatter:off
    record CodeEditorChangeDTO(
            String problemStatement,
            Map<String, String> solutionRepository,
            Map<String, String> templateRepository,
            Map<String, String> testRepository,
            String instructions
    ) {}
    // @formatter:on

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
        var exercise = session.getExercise();
        var settings = irisSettingsService.getCombinedIrisSettingsFor(exercise, false).irisCodeEditorSettings();
        var component = exerciseStep.getComponent();

        String template = switch (component) {
            case PROBLEM_STATEMENT -> settings.getProblemStatementGenerationTemplate().getContent();
            case SOLUTION_REPOSITORY -> settings.getSolutionRepoGenerationTemplate().getContent();
            case TEMPLATE_REPOSITORY -> settings.getTemplateRepoGenerationTemplate().getContent();
            case TEST_REPOSITORY -> settings.getTestRepoGenerationTemplate().getContent();
        };

        // @formatter:off
        var dto = new CodeEditorChangeDTO(
                exercise.getProblemStatement(),
                filterFiles(read(solutionRepository(exercise))),
                filterFiles(read(templateRepository(exercise))),
                filterFiles(read(testRepository(exercise))),
                exerciseStep.getInstructions()
        );
        // @formatter:on

        irisConnectorService.sendRequestV2(template, settings.getPreferredModel(), dto).handleAsync((response, err) -> {
            if (err != null) {
                log.error("Error while getting response from Iris model", err);
                irisExercisePlanStepRepository.setFailed(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepException(session, exerciseStep, err.getCause());
                return null;
            }
            if (response == null) {
                log.error("No response from Iris model");
                irisExercisePlanStepRepository.setFailed(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepException(session, exerciseStep, new IrisNoResponseException());
                return null;
            }
            log.info("Received response from iris model: {}", response.content().toPrettyString());
            try {
                String updatedProblemStatement = null;
                Set<FileChange> successfulChanges = null;
                if (component == ExerciseComponent.PROBLEM_STATEMENT) {
                    var changes = extractProblemStatementChanges(response.content());
                    log.info("Extracted problem statement changes: {}", changes);
                    updatedProblemStatement = injectChangesIntoProblemStatement(exercise, changes);
                }
                else {
                    var changes = extractFileChanges(response.content());
                    log.info("Extracted file changes for exercise {}: {}", component, changes);
                    successfulChanges = injectChangesIntoRepository(repositoryFor(exercise, component), changes);
                }
                log.info("Setting exercise step as executed");
                irisExercisePlanStepRepository.setCompleted(exerciseStep);
                irisCodeEditorWebsocketService.notifyStepSuccess(session, exerciseStep, successfulChanges, updatedProblemStatement);
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
        return Optional.ofNullable(exercise.getVcsTestRepositoryUri()).map(this::repositoryAt).orElseThrow();
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
        var url = participation.getVcsRepositoryUri();
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
    private Repository repositoryAt(VcsRepositoryUri url) {
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
                        log.error("Missing fields, could not parse ProblemStatementChange: {}", content.toPrettyString());
                        break;
                    }
                    for (JsonNode node : content.required("changes")) {
                        if (node.has("json")) {
                            try {
                                node = objectMapper.readTree(node.required("json").asText());
                            }
                            catch (JsonProcessingException e) {
                                log.error("Could not parse json field of ProblemStatementChange: {}", node.toPrettyString(), e);
                                continue;
                            }
                        }
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
                            log.error("Missing fields, could not parse ProblemStatementReplacement: {}", node.toPrettyString(), e);
                        }
                    }
                }
            }
        }
        catch (IllegalArgumentException e) {
            log.error("Missing fields, could not parse ProblemStatementChange: {}", content.toPrettyString(), e);
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
                String type = node.path("type").asText();
                if (node.has("json")) {
                    try {
                        node = objectMapper.readTree(node.required("json").asText());
                    }
                    catch (JsonProcessingException e) {
                        log.error("Could not parse json field of FileChange: {}", node.toPrettyString(), e);
                        continue;
                    }
                }
                var fileChange = switch (type) {
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
                log.error("Missing fields, could not parse FileChange: {}", node.toPrettyString(), e);
                throw new IrisParseResponseException("Parsing failed");
            }
        }
        if (changes.isEmpty()) {
            throw new IrisParseResponseException("Was not able to parse any changes");
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
    private String injectChangesIntoProblemStatement(ProgrammingExercise exercise, List<ProblemStatementChange> changes) {
        log.info("Injecting changes into problem statement: \n{}", changes);
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
        log.info("Successfully applied {} changes to problem statement, {} changes failed", successes, failures);
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
    private Set<FileChange> injectChangesIntoRepository(Repository repository, List<FileChange> changes) {
        log.info("Injecting changes into repository: \n{}", changes);
        // @formatter:off
        Map<String, Optional<File>> targetedFiles = changes.stream()
                .map(FileChange::path)
                .distinct()
                .collect(Collectors.toMap(fileName -> fileName,
                        fileName -> gitService.getFileByName(repository, fileName)));
        // @formatter:on
        Set<FileChange> successful = new HashSet<>();
        int successes = 0;
        int failures = 0;
        for (FileChange change : changes) {
            Optional<File> requestedFile = targetedFiles.get(change.path());
            try {
                change.apply(requestedFile, repositoryService, repository);
                successful.add(change);
                successes++;
            }
            catch (IOException e) {
                log.error("Encountered an IOException while applying change: {}", change, e);
            }
            catch (IrisChangeException e) {
                log.info(e.getMessage());
                failures++;
            }
        }
        log.info("Successfully applied {} changes to repository, {} changes failed", successes, failures);
        return successful;
    }

}
