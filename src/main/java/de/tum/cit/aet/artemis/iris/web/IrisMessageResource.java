package de.tum.cit.aet.artemis.iris.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMcqResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisMessageResource {

    private static final Set<String> MCQ_TYPES = Set.of("mcq", "mcq-set");

    private final IrisSessionRepository irisSessionRepository;

    private final IrisSessionService irisSessionService;

    private final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    public IrisMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionService irisSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.irisSessionRepository = irisSessionRepository;
        this.irisSessionService = irisSessionService;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * GET session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with
     *         status {@code 404 (Not Found)} if the session could not be found.
     */
    @GetMapping("sessions/{sessionId}/messages")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.ARTEMIS_EXTENSION)
    public ResponseEntity<List<IrisMessageResponseDTO>> getMessages(@PathVariable Long sessionId) {
        IrisSession session = irisSessionRepository.findByIdElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        var messages = irisMessageRepository.findAllBySessionId(sessionId);
        return ResponseEntity.ok(messages.stream().map(IrisMessageResponseDTO::of).toList());
    }

    /**
     * POST sessions/{sessionId}/messages: Send a new message from the user to the LLM
     *
     * @param sessionId  of the session
     * @param requestDTO containing message content and optional uncommitted files
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status
     *         {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("sessions/{sessionId}/messages")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.ARTEMIS_EXTENSION)
    public ResponseEntity<IrisMessageResponseDTO> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessageRequestDTO requestDTO) throws URISyntaxException {
        var session = irisSessionRepository.findByIdElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkRateLimit(session, user);

        IrisMessage message = new IrisMessage();
        var contentList = requestDTO.content() != null ? requestDTO.content() : List.<IrisMessageContentDTO>of();
        List<IrisMessageContent> contentEntities = contentList.stream().map(IrisMessageContentDTO::toEntity).toList();
        message.setContent(contentEntities);
        message.setMessageDifferentiator(requestDTO.messageDifferentiator());

        IrisMessage savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.USER);
        savedMessage.setMessageDifferentiator(message.getMessageDifferentiator());
        irisSessionService.sendOverWebsocket(savedMessage, session);
        var uncommittedFiles = requestDTO.uncommittedFiles() != null ? requestDTO.uncommittedFiles() : java.util.Map.<String, String>of();
        irisSessionService.requestMessageFromIris(session, uncommittedFiles);

        String uriString = "/api/iris/sessions/" + session.getId() + "/messages/" + savedMessage.getId();
        return ResponseEntity.created(new URI(uriString)).body(IrisMessageResponseDTO.of(savedMessage));
    }

    /**
     * POST sessions/{sessionId}/tutor-suggestion: Send a new tutor suggestion request to the LLM
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body true, or with status
     * @throws URISyntaxException if the URI syntax is incorrect
     */
    @PostMapping("sessions/{sessionId}/tutor-suggestion")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> sendTutorSuggestionMessage(@PathVariable Long sessionId) throws URISyntaxException {
        var session = irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);

        irisSessionService.requestMessageFromIris(session);

        var uriString = "/api/iris/sessions/" + session.getId() + "/tutor-suggestion";
        return ResponseEntity.created(new URI(uriString)).build();
    }

    /**
     * POST sessions/{sessionId}/messages/{messageId}/resend: Resend a message if there was previously an error when
     * sending it to the LLM
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the existing message, or with
     *         status {@code 404 (Not Found)} if the session or message could not be found.
     */
    @PostMapping("sessions/{sessionId}/messages/{messageId}/resend")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.ARTEMIS_EXTENSION)
    public ResponseEntity<IrisMessageResponseDTO> resendMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        var session = irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
        irisSessionService.checkIsIrisActivated(session);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkRateLimit(session, user);

        var message = irisMessageRepository.findByIdElseThrow(messageId);
        if (session.getMessages().lastIndexOf(message) != session.getMessages().size() - 1) {
            throw new BadRequestException("Only the last message can be resent");
        }
        if (message.getSender() != IrisMessageSender.USER) {
            throw new BadRequestException("Only user messages can be resent");
        }
        irisSessionService.requestMessageFromIris(session);

        return ResponseEntity.ok(IrisMessageResponseDTO.of(message));
    }

    /**
     * PUT sessions/{sessionId}/messages/{messageId}/helpful: Set the helpful attribute of the message
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param helpful   Request body: true if the message was helpful, false if not helpful, null if no rating.
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated message, or with status
     *         {@code 404 (Not Found)} if the session or message could not be found.
     */
    @PutMapping(value = "sessions/{sessionId}/messages/{messageId}/helpful")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.ARTEMIS_EXTENSION)
    public ResponseEntity<IrisMessageResponseDTO> rateMessage(@PathVariable Long sessionId, @PathVariable Long messageId, @RequestBody(required = false) Boolean helpful) {
        var message = irisMessageRepository.findByIdElseThrow(messageId);
        var session = message.getSession();
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The message does not belong to the session", "IrisMessage", "irisMessageSessionConflict");
        }
        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("You can only rate messages sent by Iris");
        }
        message.setHelpful(helpful);
        var savedMessage = irisMessageRepository.save(message);
        return ResponseEntity.ok(IrisMessageResponseDTO.of(savedMessage));
    }

    /**
     * PUT sessions/{sessionId}/messages/{messageId}/mcq-response: Save the user's MCQ answer selection
     *
     * @param sessionId   of the session
     * @param messageId   of the message containing the MCQ
     * @param responseDTO the user's answer selection
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}, or with status
     *         {@code 404 (Not Found)} if the session or message could not be found.
     */
    @PutMapping(value = "sessions/{sessionId}/messages/{messageId}/mcq-response")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> saveMcqResponse(@PathVariable Long sessionId, @PathVariable Long messageId, @RequestBody IrisMcqResponseDTO responseDTO) {
        var message = irisMessageRepository.findByIdElseThrow(messageId);
        var session = message.getSession();
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The message does not belong to the session", "IrisMessage", "irisMessageSessionConflict");
        }
        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);

        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("Can only save responses to LLM messages");
        }

        var jsonContent = message.getContent().stream().filter(IrisJsonMessageContent.class::isInstance).map(IrisJsonMessageContent.class::cast).findFirst()
                .orElseThrow(() -> new BadRequestException("Message has no MCQ content"));

        JsonNode root = jsonContent.getJsonNode();
        if (!(root instanceof ObjectNode rootObj)) {
            throw new BadRequestException("Message content is not a valid MCQ");
        }

        String type = rootObj.path("type").asText();
        if (!MCQ_TYPES.contains(type)) {
            throw new BadRequestException("Message content is not an MCQ");
        }

        if ("mcq".equals(type)) {
            ArrayNode options = rootObj.withArray("options");
            if (responseDTO.selectedIndex() < 0 || responseDTO.selectedIndex() >= options.size()) {
                throw new BadRequestException("Selected index is out of bounds");
            }
            ObjectNode response = objectMapper.createObjectNode();
            response.put("selectedIndex", responseDTO.selectedIndex());
            response.put("submitted", responseDTO.submitted());
            rootObj.set("response", response);
        }
        else {
            ArrayNode questions = rootObj.withArray("questions");
            if (responseDTO.questionIndex() == null || responseDTO.questionIndex() < 0 || responseDTO.questionIndex() >= questions.size()) {
                throw new BadRequestException("Question index is out of bounds");
            }
            ArrayNode questionOptions = ((ObjectNode) questions.get(responseDTO.questionIndex())).withArray("options");
            if (responseDTO.selectedIndex() < 0 || responseDTO.selectedIndex() >= questionOptions.size()) {
                throw new BadRequestException("Selected index is out of bounds");
            }

            ArrayNode responses = rootObj.withArray("responses");
            boolean updated = false;
            for (int i = 0; i < responses.size(); i++) {
                if (responses.get(i).path("questionIndex").asInt(-1) == responseDTO.questionIndex()) {
                    ((ObjectNode) responses.get(i)).put("selectedIndex", responseDTO.selectedIndex());
                    ((ObjectNode) responses.get(i)).put("submitted", responseDTO.submitted());
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                ObjectNode newResponse = objectMapper.createObjectNode();
                newResponse.put("questionIndex", responseDTO.questionIndex());
                newResponse.put("selectedIndex", responseDTO.selectedIndex());
                newResponse.put("submitted", responseDTO.submitted());
                responses.add(newResponse);
            }
        }

        jsonContent.setJsonNode(root);
        irisMessageRepository.save(message);
        return ResponseEntity.ok().build();
    }
}
