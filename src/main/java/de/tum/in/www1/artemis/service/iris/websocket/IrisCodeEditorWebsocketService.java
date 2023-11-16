package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.ExerciseComponent;
import de.tum.in.www1.artemis.domain.iris.message.IrisExercisePlanStep;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.file.FileChange;

@Service
@Profile("iris")
public class IrisCodeEditorWebsocketService extends IrisWebsocketService {

    private static final String WEBSOCKET_TOPIC_SESSION_TYPE = "code-editor-sessions";

    public IrisCodeEditorWebsocketService(WebsocketMessagingService websocketMessagingService) {
        super(websocketMessagingService);
    }

    private User checkSessionTypeAndGetUser(IrisSession session) {
        if (!(session instanceof IrisCodeEditorSession codeEditorSession)) {
            throw new UnsupportedOperationException("Only IrisCodeEditorSession is supported");
        }
        return codeEditorSession.getUser();
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param message that should be sent over the websocket
     */
    public void sendMessage(IrisMessage message) {
        var session = message.getSession();
        var user = checkSessionTypeAndGetUser(session);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), IrisWebsocketDTO.message(message));
    }

    /**
     * Notifies the client that a plan step was successfully executed, so that the client can update the UI accordingly.
     * This also includes the file changes that were applied or the updated problem statement, depending on whether the step
     * was a file change or a problem statement change.
     *
     * @param session                 to which the step belongs
     * @param step                    that was executed
     * @param fileChanges             that were applied
     * @param updatedProblemStatement that was applied
     */
    public void notifyStepSuccess(IrisCodeEditorSession session, IrisExercisePlanStep step, Set<FileChange> fileChanges, String updatedProblemStatement) {
        var messageId = step.getPlan().getMessage().getId();
        var planId = step.getPlan().getId();
        var stepId = step.getId();
        var stepSuccess = new StepExecutionSuccess(messageId, planId, stepId, step.getComponent(), fileChanges, updatedProblemStatement);
        super.send(session.getUser(), WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), IrisWebsocketDTO.stepSuccess(stepSuccess));
    }

    /**
     * Sends an exception over the websocket to a specific user
     *
     * @param session   to which the exception belongs
     * @param throwable that should be sent over the websocket
     */
    public void sendException(IrisCodeEditorSession session, Throwable throwable) {
        super.send(session.getUser(), WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), IrisWebsocketDTO.error(throwable));
    }

    /**
     * Notifies the client that a plan step could not be executed, so that the client can update the UI accordingly.
     *
     * @param session   to which the step belongs
     * @param step      that was executed
     * @param throwable that was thrown
     */
    public void notifyStepException(IrisCodeEditorSession session, IrisExercisePlanStep step, Throwable throwable) {
        var messageId = step.getPlan().getMessage().getId();
        var planId = step.getPlan().getId();
        var stepId = step.getId();
        var errorMessage = throwable.getMessage();
        var errorTranslationKey = throwable instanceof IrisException i ? i.getTranslationKey() : null;
        var translationParams = throwable instanceof IrisException i ? i.getTranslationParams() : null;
        var executionException = new StepExecutionException(messageId, planId, stepId, errorMessage, errorTranslationKey, translationParams);
        super.send(session.getUser(), WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), IrisWebsocketDTO.stepException(executionException));
    }

    public enum IrisWebsocketMessageType {
        MESSAGE, STEP_SUCCESS, STEP_EXCEPTION, EXCEPTION
    }

    private record StepExecutionSuccess(long messageId, long planId, long stepId, ExerciseComponent component, Set<FileChange> fileChanges, String updatedProblemStatement) {
    }

    private record StepExecutionException(long messageId, long planId, long stepId, String errorMessage, String errorTranslationKey, Map<String, Object> translationParams) {
    }

    public record IrisWebsocketDTO(IrisWebsocketMessageType type, IrisMessage message, StepExecutionSuccess stepExecutionSuccess, StepExecutionException executionException,
            String errorMessage, String errorTranslationKey, Map<String, Object> translationParams) {

        private static IrisWebsocketDTO message(IrisMessage message) {
            return new IrisWebsocketDTO(IrisWebsocketMessageType.MESSAGE, message, null, null, null, null, null);
        }

        private static IrisWebsocketDTO stepSuccess(StepExecutionSuccess success) {
            return new IrisWebsocketDTO(IrisWebsocketMessageType.STEP_SUCCESS, null, success, null, null, null, null);
        }

        private static IrisWebsocketDTO error(Throwable throwable) {
            return new IrisWebsocketDTO(IrisWebsocketMessageType.EXCEPTION, null, null, null, throwable.getMessage(),
                    throwable instanceof IrisException i ? i.getTranslationKey() : null, throwable instanceof IrisException i ? i.getTranslationParams() : null);
        }

        private static IrisWebsocketDTO stepException(StepExecutionException exception) {
            return new IrisWebsocketDTO(IrisWebsocketMessageType.STEP_EXCEPTION, null, null, exception, null, null, null);
        }

    }

}
