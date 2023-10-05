package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisCodeEditorMessageResource {

    private final IrisSessionRepository irisSessionRepository;

    private final IrisSessionService irisSessionService;

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    private final IrisWebsocketService irisWebsocketService;

    private final IrisMessageContentRepository irisMessageContentRepository;

    private final IrisExercisePlanComponentRepository irisExercisePlanComponentRepository;

    public IrisCodeEditorMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionService irisSessionService,
            IrisCodeEditorSessionService irisCodeEditorSessionService, IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository,
            IrisWebsocketService irisWebsocketService, IrisMessageContentRepository irisMessageContentRepository,
            IrisExercisePlanComponentRepository irisExercisePlanComponentRepository) {
        this.irisSessionRepository = irisSessionRepository;
        this.irisSessionService = irisSessionService;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
        this.irisWebsocketService = irisWebsocketService;
        this.irisMessageContentRepository = irisMessageContentRepository;
        this.irisExercisePlanComponentRepository = irisExercisePlanComponentRepository;
    }

    /**
     * GET code-editor-sessions/{sessionId}/messages: Retrieve the messages for the iris code editor session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @GetMapping("code-editor-sessions/{sessionId}/messages")
    @EnforceAtLeastEditor
    public ResponseEntity<List<IrisMessage>> getMessages(@PathVariable Long sessionId) {
        IrisSession session = irisSessionRepository.findByIdElseThrow(sessionId);
        // irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        var messages = irisMessageRepository.findAllExceptSystemMessagesWithContentBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * POST code-editor-sessions/{sessionId}/messages: Send a new message from the user to the LLM
     *
     * @param sessionId of the session
     * @param message   to send
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be
     *         found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages/")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) throws URISyntaxException {
        var session = irisSessionRepository.findByIdElseThrow(sessionId);
        // irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.USER);
        irisSessionService.requestMessageFromIris(session);
        savedMessage.setMessageDifferentiator(message.getMessageDifferentiator());
        irisWebsocketService.sendMessage(savedMessage);

        var uriString = "/api/iris/code-editor-sessions/" + session.getId() + "/messages/" + savedMessage.getId();
        return ResponseEntity.created(new URI(uriString)).body(savedMessage);
    }

    /**
     * POST code-editor-sessions/{sessionId}/messages/{messageId}/resend: Resend a message if there was previously an error when sending it to the LLM
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the existing message, or with status {@code 404 (Not Found)} if the session or message could
     *         not be found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages/{messageId}/resend")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisMessage> resendMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        var session = irisSessionRepository.findByIdWithMessagesElseThrow(sessionId);
        // irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        var message = irisMessageRepository.findByIdElseThrow(messageId);
        if (session.getMessages().lastIndexOf(message) != session.getMessages().size() - 1) {
            throw new BadRequestException("Only the last message can be resent");
        }
        if (message.getSender() != IrisMessageSender.USER) {
            throw new BadRequestException("Only user messages can be resent");
        }
        irisSessionService.requestMessageFromIris(session);
        message.setMessageDifferentiator(message.getMessageDifferentiator());

        return ResponseEntity.ok(message);
    }

    /**
     * Put code-editor-sessions/{sessionId}/messages/{messageId}/contents/{contentId}/execute: Send the (updated) plan message to the LLM
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param contentId of the content
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages/{messageId}/contents/{contentId}/execute")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> executePlan(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long contentId) {
        var fromDB = irisMessageContentRepository.findByIdElseThrow(contentId);
        if (!(fromDB instanceof IrisExercisePlanMessageContent exercisePlan)) {
            throw new BadRequestException("Content is not an exercise plan");
        }
        var message = exercisePlan.getMessage();
        var session = message.getSession();
        if (!Objects.equals(message.getId(), messageId)) {
            throw new ConflictException("The specified messageId is incorrect", "IrisMessageContent", "irisMessageContentConflict");
        }
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The specified sessionId is incorrect", "IrisMessage", "irisMessageSessionConflict");
        }
        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("You can only edit the plan messages sent by Iris");
        }

        // irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        irisCodeEditorSessionService.requestAndHandleResponse(session);// TODO: second request for plan realization
        return ResponseEntity.ok(null);
    }

    /**
     * PUT code-editor-sessions/{sessionId}/messages/{messageId}/contents/{contentId}/components/{componentId}: Set the component instruction of the ExercisePlanComponent
     *
     * @param sessionId   of the session
     * @param messageId   of the message
     * @param contentId   of the content
     * @param componentId of the exercisePlanComponent
     * @param plan        to set for the corresponding component, if cancel the plan of the component, the value would be null
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated component, or with status {@code 404 (Not Found)} if the component could not
     *         be found.
     */
    @PutMapping(value = { "code-editor-sessions/{sessionId}/messages/{messageId}/contents/{contentId}/components/{componentId}" })
    @EnforceAtLeastEditor
    public ResponseEntity<ExercisePlanComponent> updateComponentPlan(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long contentId,
            @PathVariable Long componentId, @RequestBody String plan) {
        var message = irisMessageRepository.findByIdElseThrow(messageId);
        var session = message.getSession();
        var content = irisMessageContentRepository.findByIdElseThrow(contentId);
        var component = irisExercisePlanComponentRepository.findByIdElseThrow(componentId);
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The message does not belong to the session", "IrisMessage", "irisMessageSessionConflict");
        }
        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("You can only edit the plan messages sent by Iris");
        }
        // irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        if (content instanceof IrisExercisePlanMessageContent) {
            var exercisePlanId = component.getExercisePlan().getId();
            if (!Objects.equals(exercisePlanId, contentId)) {
                throw new ConflictException("The component plan does not belong to the exercise plan", "ExercisePlanComponent", "irisComponentPlanExercisePlanConflict");
            }
            component.setInstructions(plan);
            var savedExercisePlanComponent = irisExercisePlanComponentRepository.save(component);
            return ResponseEntity.ok(savedExercisePlanComponent);
        }
        else {
            throw new BadRequestException("You can only edit component plan content");
        }
    }
}
