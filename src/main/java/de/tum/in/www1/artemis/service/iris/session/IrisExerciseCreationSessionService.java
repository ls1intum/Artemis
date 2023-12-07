package de.tum.in.www1.artemis.service.iris.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.iris.IrisDefaultTemplateService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.session.exercisecreation.IrisExerciseMetadataDTO;
import de.tum.in.www1.artemis.service.iris.session.exercisecreation.IrisExerciseUpdateDTO;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisExerciseCreationWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service to handle the exercise creation feature of Iris.
 */
@Service
@Profile("iris")
public class IrisExerciseCreationSessionService implements IrisSessionSubServiceInterface {

    private final Logger log = LoggerFactory.getLogger(IrisCodeEditorSessionService.class);

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
        irisExerciseCreationWebsocketService.sendMessage(message, null);
    }

    /**
     * Sends a request to Iris to adapt the exercise metadata and the draft problem statement.
     *
     * @param irisSession  The session to get a message for
     * @param clientParams The current exercise metadata and problem statement
     */
    @Override
    public void requestAndHandleResponse(IrisSession irisSession, Map<String, Object> clientParams) {
        var fromDB = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        if (!(fromDB instanceof IrisExerciseCreationSession session)) {
            throw new BadRequestException("Iris session is not an exercise creation session");
        }
        var template = irisDefaultTemplateService.load("exercise-creation.hbs");
        var settings = irisSettingsService.getCombinedIrisSettingsFor(session.getCourse(), false).irisCodeEditorSettings();
        Map<String, Object> params = new HashMap<>();
        params.put("metadata", clientParams.get("metadata"));
        params.put("problemStatement", clientParams.get("problemStatement"));
        params.put("chatHistory", session.getMessages());
        irisConnectorService.sendRequestV2(template.getContent(), settings.getPreferredModel(), params).handleAsync((response, throwable) -> {
            if (throwable != null) {
                log.error("Failed to get Iris response", throwable);
                irisExerciseCreationWebsocketService.sendException(session, throwable);
                return null;
            }
            if (response == null || response.content() == null) {
                log.error("Iris response is null");
                irisExerciseCreationWebsocketService.sendException(session, new IrisParseResponseException("Iris did not respond"));
                return null;
            }
            log.info("Received Iris response: {}", response);
            if (!response.content().has("response")) {
                log.error("Iris response did not have a response message for the user");
                irisExerciseCreationWebsocketService.sendException(session, new IrisParseResponseException("Iris did not respond"));
                return null;
            }
            var irisMessage = toIrisMessage(response.content());
            var exerciseUpdate = toIrisExerciseUpdateDTO(response.content());
            var saved = irisMessageService.saveMessage(irisMessage, session, IrisMessageSender.LLM);
            irisExerciseCreationWebsocketService.sendMessage(saved, exerciseUpdate);
            return null;
        });
    }

    private IrisMessage toIrisMessage(JsonNode content) {
        var message = new IrisMessage();
        var chatWindowResponse = content.required("response").asText();
        message.addContent(new IrisTextMessageContent(chatWindowResponse));
        return message;
    }

    private IrisExerciseUpdateDTO toIrisExerciseUpdateDTO(JsonNode content) {
        String problemStatement = null;
        if (content.hasNonNull("finalProblemStatement")) {
            problemStatement = content.get("finalProblemStatement").asText();
        }
        try {
            String updatedMetadata = content.required("updatedMetadata").asText();
            JsonNode metadataJson = objectMapper.readTree(updatedMetadata);
            var metadata = IrisExerciseMetadataDTO.parse(metadataJson);
            return new IrisExerciseUpdateDTO(problemStatement, metadata);
        }
        catch (JsonProcessingException e) {
            log.warn("Failed to parse updatedMetadata, was not valid JSON", e);
        }
        catch (IllegalArgumentException e) {
            log.warn("Failed to parse updatedMetadata, did not have all required fields", e);
        }
        return new IrisExerciseUpdateDTO(problemStatement, null);
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
