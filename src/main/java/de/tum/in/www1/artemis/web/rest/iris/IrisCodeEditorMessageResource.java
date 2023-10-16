package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisCodeEditorWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisCodeEditorMessageResource extends IrisMessageResource {

    private final IrisCodeEditorWebsocketService irisCodeEditorWebsocketService;

    private final IrisMessageContentRepository irisMessageContentRepository;

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisExercisePlanComponentRepository irisExercisePlanComponentRepository;

    public IrisCodeEditorMessageResource(IrisSessionRepository irisSessionRepository, IrisSessionService irisSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisRateLimitService rateLimitService, UserRepository userRepository,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, IrisMessageContentRepository irisMessageContentRepository,
            IrisCodeEditorSessionService irisCodeEditorSessionService, IrisExercisePlanComponentRepository irisExercisePlanComponentRepository) {
        super(irisSessionRepository, irisSessionService, irisMessageService, irisMessageRepository, rateLimitService, userRepository);
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.irisMessageContentRepository = irisMessageContentRepository;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisExercisePlanComponentRepository = irisExercisePlanComponentRepository;
    }

    /**
     * POST code-editor-sessions/{sessionId}/messages: Send a new message from the user to the LLM
     *
     * @param sessionId of the session
     * @param message   to send
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) throws URISyntaxException {
        return super.createMessage(sessionId, message);
    }

    @Override
    String sendMessageAndReturnUri(IrisSession session, IrisMessage savedMessage) {
        irisCodeEditorWebsocketService.sendMessage(savedMessage);
        return "/api/iris/code-editor-sessions/" + session.getId() + "/messages/" + savedMessage.getId();
    }

    /**
     * GET code-editor-session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the list of messages, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @GetMapping("code-editor-sessions/{sessionId}/messages")
    @EnforceAtLeastEditor
    public ResponseEntity<List<IrisMessage>> getMessages(@PathVariable Long sessionId) {
        return super.getMessages(sessionId);
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
        return super.resendMessage(sessionId, messageId);
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
        if (!Objects.equals(message.getId(), messageId)) {
            throw new ConflictException("The specified messageId is incorrect", "IrisMessageContent", "irisMessageContentConflict");
        }
        var session = message.getSession();
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The specified sessionId is incorrect", "IrisMessage", "irisMessageSessionConflict");
        }
        if (!(session instanceof IrisCodeEditorSession codeEditorSession)) {
            throw new BadRequestException("Session is not a code editor session");
        }
        if (!exercisePlan.hasNext()) {
            throw new BadRequestException("The exercise plan has no next step");
        }

        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);
        irisCodeEditorSessionService.requestExerciseChanges(codeEditorSession, exercisePlan);
        return ResponseEntity.ok(null);
    }

    /**
     * PUT code-editor-sessions/{sessionId}/messages/{messageId}/contents/{contentId}/components/{componentId}: Set the component instruction of the ExercisePlanComponent
     *
     * @param sessionId     of the session
     * @param messageId     of the message
     * @param contentId     of the content
     * @param componentId   of the exercisePlanComponent
     * @param planComponent to set for the corresponding component
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated component, or with status {@code 404 (Not Found)} if the component could not
     *         be found.
     */
    @PutMapping(value = { "code-editor-sessions/{sessionId}/messages/{messageId}/contents/{contentId}/components/{componentId}" })
    @EnforceAtLeastEditor
    public ResponseEntity<IrisExercisePlanComponent> updateExercisePlanComponent(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long contentId,
            @PathVariable Long componentId, @RequestBody IrisExercisePlanComponent planComponent) {
        var exercisePlanComponent = irisExercisePlanComponentRepository.findByIdElseThrow(componentId);
        var exercisePlan = exercisePlanComponent.getExercisePlan();
        if (!Objects.equals(exercisePlan.getId(), contentId)) {
            throw new ConflictException("The specified contentId is incorrect", "IrisExercisePlanComponent", "irisExercisePlanComponentConflict");
        }
        var message = exercisePlan.getMessage();
        if (!Objects.equals(message.getId(), messageId)) {
            throw new ConflictException("The specified messageId is incorrect", "IrisMessageContent", "irisMessageContentConflict");
        }
        if (message.getSender() != IrisMessageSender.LLM) {
            throw new BadRequestException("You can only edit the plan messages sent by Iris");
        }
        var session = message.getSession();
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The specified sessionId is incorrect", "IrisMessage", "irisMessageSessionConflict");
        }

        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);

        exercisePlanComponent.setInstructions(planComponent.getInstructions());
        var savedExercisePlanComponent = irisExercisePlanComponentRepository.save(exercisePlanComponent);
        return ResponseEntity.ok(savedExercisePlanComponent);
    }
}
