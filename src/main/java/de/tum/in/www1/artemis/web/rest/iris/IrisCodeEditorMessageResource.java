package de.tum.in.www1.artemis.web.rest.iris;

import java.util.Objects;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanStep;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisExercisePlanStepRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/")
public class IrisCodeEditorMessageResource {

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisExercisePlanStepRepository irisExercisePlanStepRepository;

    private final UserRepository userRepository;

    public IrisCodeEditorMessageResource(IrisCodeEditorSessionService irisCodeEditorSessionService, IrisExercisePlanStepRepository irisExercisePlanStepRepository,
            UserRepository userRepository) {
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisExercisePlanStepRepository = irisExercisePlanStepRepository;
        this.userRepository = userRepository;
    }

    /**
     * Put code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}/execute: Execute a
     * step of an exercise plan
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param planId    of the content
     * @param stepId    of the plan step
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the created message, or with status
     *         {@code 404 (Not Found)} if the session could not be found.
     */
    @PostMapping("code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}/execute")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> executeExercisePlanStep(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long planId, @PathVariable Long stepId) {
        var step = irisExercisePlanStepRepository.findByIdElseThrow(stepId);
        var session = checkIdsAndGetSession(sessionId, messageId, planId, step);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisCodeEditorSessionService.checkIsFeatureActivatedFor(session);
        irisCodeEditorSessionService.checkHasAccessTo(user, session);
        irisCodeEditorSessionService.requestChangesToExerciseComponent(session, step);
        // Return with empty body now, the changes will be sent over the websocket when they are ready
        return ResponseEntity.ok(null);
    }

    /**
     * PUT code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}: Update the
     * instructions of an exercise plan step
     *
     * @param sessionId   of the session
     * @param messageId   of the message
     * @param planId      of the exercise plan
     * @param stepId      of the plan step
     * @param updatedStep the plan step with updated instructions
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated component, or with
     *         status {@code 404 (Not Found)} if the component could not be found.
     */
    @PutMapping("code-editor-sessions/{sessionId}/messages/{messageId}/contents/{planId}/steps/{stepId}")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisExercisePlanStep> updateExercisePlanStep(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long planId,
            @PathVariable Long stepId, @RequestBody IrisExercisePlanStep updatedStep) {
        var step = irisExercisePlanStepRepository.findByIdElseThrow(stepId);
        var session = checkIdsAndGetSession(sessionId, messageId, planId, step);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisCodeEditorSessionService.checkIsFeatureActivatedFor(session);
        irisCodeEditorSessionService.checkHasAccessTo(user, session);

        step.setInstructions(updatedStep.getInstructions());

        var savedStep = irisExercisePlanStepRepository.save(step);
        return ResponseEntity.ok(savedStep);
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
