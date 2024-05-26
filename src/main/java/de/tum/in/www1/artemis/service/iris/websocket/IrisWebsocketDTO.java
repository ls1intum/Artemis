package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.List;
import java.util.Objects;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

/**
 * A DTO for sending status updates of Iris to the client via the websocket
 *
 * @param type          the type of the message
 * @param message       an IrisMessage instance if the type is MESSAGE
 * @param rateLimitInfo the rate limit information
 * @param stages        the stages of the Pyris pipeline
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisWebsocketDTO(IrisWebsocketMessageType type, IrisMessage message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {

    /**
     * Creates a new IrisWebsocketDTO instance with the given parameters
     * Takes care of setting the type correctly
     *
     * @param message       the IrisMessage (optional)
     * @param rateLimitInfo the rate limit information
     * @param stages        the stages of the Pyris pipeline
     */
    public IrisWebsocketDTO(@Nullable IrisMessage message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {
        this(determineType(message), message, rateLimitInfo, stages);
    }

    /**
     * Determines the type of WebSocket message based on the presence of a message or throwable.
     * <p>
     * This method categorizes the WebSocket message type as follows:
     * <ul>
     * <li>{@link IrisWebsocketMessageType#MESSAGE} if the {@code message} parameter is not null.</li>
     * <li>{@link IrisWebsocketMessageType#STATUS} if both {@code message} and {@code throwable} are null.</li>
     * </ul>
     *
     * @param message The message associated with the WebSocket, which may be null.
     * @return The {@link IrisWebsocketMessageType} indicating the type of the message based on the given parameters.
     */
    private static IrisWebsocketMessageType determineType(@Nullable IrisMessage message) {
        if (message != null) {
            return IrisWebsocketMessageType.MESSAGE;
        }
        return IrisWebsocketMessageType.STATUS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IrisWebsocketDTO that = (IrisWebsocketDTO) o;
        return type == that.type && Objects.equals(message, that.message) && Objects.equals(rateLimitInfo, that.rateLimitInfo) && Objects.equals(stages, that.stages);
    }

    public enum IrisWebsocketMessageType {
        MESSAGE, STATUS
    }
}
