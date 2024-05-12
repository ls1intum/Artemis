package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;

/**
 * A DTO for sending status updates of Iris to the client via the websocket
 *
 * @param type                the type of the message
 * @param message             an IrisMessage instance if the type is MESSAGE
 * @param errorMessage        the error message if the type is ERROR
 * @param errorTranslationKey the translation key for the error message if the type is ERROR
 * @param translationParams   the translation parameters for the error message if the type is ERROR
 * @param rateLimitInfo       the rate limit information
 * @param stages              the stages of the Pyris pipeline
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisWebsocketDTO(IrisWebsocketMessageType type, IrisMessage message, String errorMessage, String errorTranslationKey, Map<String, Object> translationParams,
        IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {

    /**
     * Creates a new IrisWebsocketDTO instance with the given parameters
     * Takes care of setting the type correctly and also extracts the error message and translation key from the throwable (if present)
     *
     * @param message       the IrisMessage (optional)
     * @param throwable     the Throwable (optional)
     * @param rateLimitInfo the rate limit information
     * @param stages        the stages of the Pyris pipeline
     */
    public IrisWebsocketDTO(@Nullable IrisMessage message, @Nullable Throwable throwable, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {
        this(determineType(message, throwable), message, throwable == null ? null : throwable.getMessage(),
                throwable instanceof IrisException irisException ? irisException.getTranslationKey() : null,
                throwable instanceof IrisException irisException ? irisException.getTranslationParams() : null, rateLimitInfo, stages);
    }

    /**
     * Determines the type of WebSocket message based on the presence of a message or throwable.
     * <p>
     * This method categorizes the WebSocket message type as follows:
     * <ul>
     * <li>{@link IrisWebsocketMessageType#MESSAGE} if the {@code message} parameter is not null.</li>
     * <li>{@link IrisWebsocketMessageType#ERROR} if the {@code message} is null and {@code throwable} is not null.</li>
     * <li>{@link IrisWebsocketMessageType#STATUS} if both {@code message} and {@code throwable} are null.</li>
     * </ul>
     *
     * @param message   The message associated with the WebSocket, which may be null.
     * @param throwable The throwable associated with the WebSocket, which may also be null.
     * @return The {@link IrisWebsocketMessageType} indicating the type of the message based on the given parameters.
     */
    private static IrisWebsocketMessageType determineType(@Nullable IrisMessage message, @Nullable Throwable throwable) {
        if (message != null) {
            return IrisWebsocketMessageType.MESSAGE;
        }
        else if (throwable != null) {
            return IrisWebsocketMessageType.ERROR;
        }
        else {
            return IrisWebsocketMessageType.STATUS;
        }
    }

    @Override
    public Map<String, Object> translationParams() {
        return translationParams != null ? Collections.unmodifiableMap(translationParams) : null;
    }

    public enum IrisWebsocketMessageType {
        MESSAGE, STATUS, ERROR
    }
}
