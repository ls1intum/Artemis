package de.tum.in.www1.artemis.web.rest.iris;

import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisMessageContentRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisExercisePlanResource {

    private final IrisSessionService irisSessionService;

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisMessageContentRepository irisMessageContentRepository;

    public IrisExercisePlanResource(IrisSessionService irisSessionService, IrisCodeEditorSessionService irisCodeEditorSessionService,
            IrisMessageContentRepository irisMessageContentRepository) {
        this.irisSessionService = irisSessionService;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisMessageContentRepository = irisMessageContentRepository;
    }

    @PostMapping("code-editor-sessions/{sessionId}/messages/{messageId}/plan/{planId}/execute")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> executePlan(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long planId) {
        var existingPlan = validateRequest(sessionId, messageId, planId);

        // Execute the plan
        // TODO

        return ResponseEntity.ok(null);
    }

    /**
     * PUT session/{sessionId}/message/{messageId}/plan/{planId}: Update the exercise plan in the message.
     *
     * @param sessionId the ID of the session
     * @param messageId the ID of the message
     * @param planId    the ID of the plan
     * @param newPlan   the new plan
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated plan,
     *         or with status {@code 404 (Not Found)} if the session or message or content could not be found
     */
    @PutMapping("code-editor-sessions/{sessionId}/messages/{messageId}/plan/{planId}")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisExercisePlanMessageContent> updatePlan(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Long planId,
            @RequestBody IrisExercisePlanMessageContent newPlan) {
        var existingPlan = validateRequest(sessionId, messageId, planId);

        // Update the plan
        // TODO

        // Overwrite the updated plan in the database
        var savedPlan = irisMessageContentRepository.save(existingPlan);
        return ResponseEntity.ok(savedPlan);
    }

    private IrisExercisePlanMessageContent validateRequest(Long sessionId, Long messageId, Long planId) {
        // Fetch the plan in its current state from the database
        var fromDB = irisMessageContentRepository.findByIdElseThrow(planId);
        if (!(fromDB instanceof IrisExercisePlanMessageContent plan)) {
            // planId passed was not the ID of an actual exercise plan
            throw new BadRequestException("The content with the specified ID is not an exercise plan");
        }

        // Get the owning message and session, make sure they match the IDs passed
        var session = checkOwnershipAndReturnSession(sessionId, messageId, plan);

        // Make sure the session is active and the user has access
        irisSessionService.checkIsIrisActivated(session);
        irisSessionService.checkHasAccessToIrisSession(session, null);

        return plan;
    }

    private static IrisSession checkOwnershipAndReturnSession(Long sessionId, Long messageId, IrisMessageContent existingPlan) {
        var message = existingPlan.getMessage();
        if (!Objects.equals(message.getId(), messageId)) {
            throw new ConflictException("The exercise plan does not belong to the message", "IrisExercisePlanMessageContent", "irisMessageContentConflict");
        }
        var session = message.getSession();
        if (!Objects.equals(session.getId(), sessionId)) {
            throw new ConflictException("The message does not belong to the session", "IrisMessage", "irisMessageSessionConflict");
        }
        return session;
    }
}
