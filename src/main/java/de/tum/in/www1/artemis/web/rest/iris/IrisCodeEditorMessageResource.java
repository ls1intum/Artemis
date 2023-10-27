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
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
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

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisExercisePlanStepRepository irisExercisePlanStepRepository;

    public IrisCodeEditorMessageResource(IrisSessionRepository irisSessionRepository, IrisCodeEditorSessionService irisSessionService, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisRateLimitService rateLimitService, UserRepository userRepository,
            IrisCodeEditorWebsocketService irisCodeEditorWebsocketService, IrisCodeEditorSessionService irisCodeEditorSessionService,
            IrisExercisePlanStepRepository irisExercisePlanStepRepository) {
        super(irisSessionRepository, irisSessionService, irisMessageService, irisMessageRepository, rateLimitService, userRepository);
        this.irisCodeEditorWebsocketService = irisCodeEditorWebsocketService;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisExercisePlanStepRepository = irisExercisePlanStepRepository;
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
     * POST code-editor-sessions/{sessionId}/messages: Send a new message from the user to the LLM
     *
     * @param sessionId of the session
     * @param message   message from the user
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) throws URISyntaxException {
        var fromDB = irisSessionRepository.findByIdWithMessagesAndContentsElseThrow(sessionId);
        if (!(fromDB instanceof IrisCodeEditorSession session)) {
            throw new BadRequestException("Session is not a code editor session");
        }
        var savedMessage = super.postMessage(session, message);

        irisCodeEditorSessionService.converseWithModel(session);
        irisCodeEditorWebsocketService.sendMessage(savedMessage);
        String uriString = "/api/iris/code-editor-sessions/" + sessionId + "/messages/" + savedMessage.getId();
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
        var fromDB = irisSessionRepository.findByIdWithMessagesAndContentsElseThrow(sessionId);
        if (!(fromDB instanceof IrisCodeEditorSession session)) {
            throw new BadRequestException("Session is not a code editor session");
        }
        var message = super.getMessageToResend(session, messageId);
        irisCodeEditorSessionService.converseWithModel(session);
        return ResponseEntity.ok(message);
    }

    /**
     * Put code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}/execute:
     * Execute a step of an exercise plan
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param planId    of the content
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}/execute")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> executeExercisePlanStep(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long planId, @PathVariable Long stepId) {
        var step = irisExercisePlanStepRepository.findByIdElseThrow(stepId);
        var session = checkIdsAndGetSession(sessionId, messageId, planId, step);

        irisCodeEditorSessionService.checkIsIrisActivated(session);
        irisCodeEditorSessionService.checkHasAccessToIrisSession(session, null);
        irisCodeEditorSessionService.requestChangesToExerciseComponent(session, step);
        // Return with empty body now, the changes will be sent over the websocket when they are ready
        return ResponseEntity.ok(null);
    }

    /**
     * PUT code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}:
     * Update the instructions of an exercise plan step
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param planId    of the exercise plan
     * @param stepId    of the plan step
     * @param planStep  the plan step with updated instructions
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated component, or with status {@code 404 (Not Found)} if the component could not
     *         be found.
     */
    @PutMapping("code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisExercisePlanStep> updateExercisePlanStepInstructions(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long planId,
            @PathVariable Long stepId, @RequestBody IrisExercisePlanStep planStep) {
        var step = irisExercisePlanStepRepository.findByIdElseThrow(stepId);
        var session = checkIdsAndGetSession(sessionId, messageId, planId, step);

        irisCodeEditorSessionService.checkIsIrisActivated(session);
        irisCodeEditorSessionService.checkHasAccessToIrisSession(session, null);

        step.setInstructions(planStep.getInstructions());
        var savedExercisePlanComponent = irisExercisePlanStepRepository.save(step);
        return ResponseEntity.ok(savedExercisePlanComponent);
    }

    private static IrisCodeEditorSession checkIdsAndGetSession(Long sessionId, Long messageId, Long planId, IrisExercisePlanStep step) {
        var plan = step.getPlan();
        if (!Objects.equals(plan.getId(), planId)) {
            throw new ConflictException("The specified contentId is incorrect", "IrisExercisePlanComponent", "irisExercisePlanComponentConflict");
        }
        var message = plan.getMessage();
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
        return codeEditorSession;
    }
}
