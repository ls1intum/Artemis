package de.tum.in.www1.artemis.service.iris.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseCreationSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.iris.IrisExerciseCreationSessionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisMessageResponseV2DTO;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.iris.IrisDefaultTemplateService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.session.exercisecreation.IrisExerciseMetadataDTO;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisExerciseCreationWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service to handle the exercise creation feature of Iris.
 */
@Service
@Profile("iris")
public class IrisExerciseCreationSessionService implements IrisSessionSubServiceInterface {

    private static String loadTemplate(String fileName) {
        try {
            return Files.readString(Path.of("src", "main", "resources", "templates", "iris", fileName)).trim();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load Iris template", e);
        }
    }

    private final Logger log = LoggerFactory.getLogger(IrisExerciseCreationSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisExerciseCreationWebsocketService irisExerciseCreationWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisExerciseCreationSessionRepository irisExerciseCreationSessionRepository;

    private final VersionControlService versionControlService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisDefaultTemplateService irisDefaultTemplateService;

    private final ObjectMapper objectMapper;

    public IrisExerciseCreationSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisExerciseCreationWebsocketService irisExerciseCreationWebsocketService, AuthorizationCheckService authCheckService,
            IrisExerciseCreationSessionRepository irisExerciseCreationSessionRepository, VersionControlService versionControlService, GitService gitService,
            RepositoryService repositoryService, TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository, IrisSessionRepository irisSessionRepository,
            IrisDefaultTemplateService irisDefaultTemplateService, ObjectMapper objectMapper) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisExerciseCreationWebsocketService = irisExerciseCreationWebsocketService;
        this.authCheckService = authCheckService;
        this.irisExerciseCreationSessionRepository = irisExerciseCreationSessionRepository;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.templateParticipationRepository = templateParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.irisSessionRepository = irisSessionRepository;
        this.irisDefaultTemplateService = irisDefaultTemplateService;
        this.objectMapper = objectMapper;
    }

    public IrisExerciseCreationSession createSession(Course course, User user) {
        return irisExerciseCreationSessionRepository.save(new IrisExerciseCreationSession(course, user));
    }

    @Override
    public void sendOverWebsocket(IrisMessage message) {
        irisExerciseCreationWebsocketService.sendMessage(message, null, null);
    }

    private record IrisExerciseCreationRequestDTO(String problemStatement, ObjectNode metadata, List<IrisMessage> chatHistory) {
    }

    /**
     * Sends a request to Iris to adapt the exercise metadata and the draft problem statement.
     *
     * @param irisSession  The session to get a message for
     * @param clientParams The current exercise metadata and problem statement
     */
    @Override
    public void requestAndHandleResponse(IrisSession irisSession, JsonNode clientParams) {
        var fromDB = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        if (!(fromDB instanceof IrisExerciseCreationSession session)) {
            throw new BadRequestException("Iris session is not an exercise creation session");
        }
        if (!clientParams.path("metadata").isObject()) {
            throw new BadRequestException("Exercise metadata is not an object: " + clientParams.path("metadata"));
        }

        var settings = irisSettingsService.getCombinedIrisSettingsFor(session.getCourse(), false).irisCodeEditorSettings();
        String template = loadTemplate("exercise-creation-chat.hbs");
        String preferredModel = settings.getPreferredModel();
        String problemStatement = clientParams.path("problemStatement").asText();
        ObjectNode metadata = (ObjectNode) clientParams.path("metadata");
        var params = new IrisExerciseCreationRequestDTO(problemStatement, metadata, session.getMessages());

        irisConnectorService.sendRequestV2(template, preferredModel, params).handleAsync((chatResponse, chatErr) -> {
            if (!checkPyrisResponse(chatResponse, chatErr, session)) {
                return null;
            }
            IrisMessage chatMessage = receiveIrisMessage(chatResponse.content(), session);
            irisExerciseCreationWebsocketService.sendMessage(chatMessage, null, null);

            var adapt = chatResponse.content().path("wantsToAdapt").asBoolean();
            if (!adapt) {
                return null;
            }
            var adaptTemplate = loadTemplate("exercise-creation-adapt.hbs");
            var adaptParams = new IrisExerciseCreationRequestDTO(problemStatement, metadata, chatMessage.getSession().getMessages());
            return irisConnectorService.sendRequestV2(adaptTemplate, preferredModel, adaptParams).handleAsync((adaptResponse, adaptErr) -> {
                if (!checkPyrisResponse(adaptResponse, adaptErr, session)) {
                    return null;
                }
                IrisMessage adaptMessage = receiveIrisMessage(adaptResponse.content(), session);
                String updatedProblemStatement = getProblemStatement(adaptResponse.content());
                IrisExerciseMetadataDTO updatedMetadata = getUpdatedMetadata(adaptResponse.content(), metadata);
                irisExerciseCreationWebsocketService.sendMessage(adaptMessage, updatedProblemStatement, updatedMetadata);
                return null;
            });
        });
    }

    private IrisMessage receiveIrisMessage(JsonNode content, IrisSession session) {
        String adaptResponseText = content.get("response").asText();
        IrisMessage adaptMessage = new IrisMessage().withContent(new IrisTextMessageContent(adaptResponseText));
        return irisMessageService.saveMessage(adaptMessage, session, IrisMessageSender.LLM);
    }

    private boolean checkPyrisResponse(IrisMessageResponseV2DTO response, Throwable throwable, IrisExerciseCreationSession session) {
        if (throwable != null) {
            log.error("Failed to get Iris response", throwable);
            irisExerciseCreationWebsocketService.sendException(session, throwable);
            return false;
        }
        if (response == null || response.content() == null) {
            log.error("Iris response is null");
            irisExerciseCreationWebsocketService.sendException(session, new IrisParseResponseException("Iris did not respond"));
            return false;
        }
        log.info("Received Iris response: {}", response);
        if (!response.content().has("response")) {
            log.error("Iris response did not have a response message for the user");
            irisExerciseCreationWebsocketService.sendException(session, new IrisParseResponseException("Iris did not respond"));
            return false;
        }
        return true;
    }

    private String getProblemStatement(JsonNode content) {
        if (content.hasNonNull("updatedProblemStatement")) {
            return content.get("updatedProblemStatement").asText();
        }
        return null;
    }

    private IrisExerciseMetadataDTO getUpdatedMetadata(JsonNode content, ObjectNode currentMetadata) {
        try {
            JsonNode updatedMetadata = objectMapper.readTree(content.required("updatedMetadata").asText());
            if (!updatedMetadata.isObject()) {
                throw new IllegalArgumentException("Updated metadata is not an object: " + updatedMetadata.getNodeType());
            }
            // Merge the updated metadata with the current metadata
            ObjectNode mergedMetadata = objectMapper.createObjectNode();
            mergedMetadata.setAll(currentMetadata);
            mergedMetadata.setAll((ObjectNode) updatedMetadata);
            return objectMapper.treeToValue(mergedMetadata, IrisExerciseMetadataDTO.class);
        }
        catch (JsonProcessingException e) {
            log.warn("Failed to parse updatedMetadata, did not match Metadata type", e);
        }
        catch (IllegalArgumentException e) {
            log.warn("Failed to parse updatedMetadata, did not have all required fields", e);
        }
        return null;
    }

    @Override
    public void checkHasAccessToIrisSession(IrisSession irisSession, User user) {
        var session = castToSessionType(irisSession, IrisExerciseCreationSession.class);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, session.getCourse(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Exercise Creation Session", session.getId());
        }
    }

    @Override
    public void checkRateLimit(User user) {
        // No rate limit implemented yet for exercise creation
    }

    @Override
    public void checkIsIrisActivated(IrisSession irisSession) {
        var session = castToSessionType(irisSession, IrisExerciseCreationSession.class);
        if (!irisSettingsService.getCombinedIrisSettingsFor(session.getCourse(), false).irisCodeEditorSettings().isEnabled()) {
            throw new UnsupportedOperationException("Iris exercise creation is not activated");
        }
    }

}
