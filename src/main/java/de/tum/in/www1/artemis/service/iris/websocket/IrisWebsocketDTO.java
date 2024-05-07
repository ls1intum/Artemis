package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * @return the created IrisWebsocketDTO instance
     */
    public static IrisWebsocketDTO create(IrisMessage message, Throwable throwable, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {
        IrisWebsocketMessageType type;
        if (message != null) {
            type = IrisWebsocketMessageType.MESSAGE;
        }
        else if (throwable != null) {
            type = IrisWebsocketMessageType.ERROR;
        }
        else {
            type = IrisWebsocketMessageType.STATUS;
        }
        var errorMessage = throwable == null ? null : throwable.getMessage();
        var errorTranslationKey = throwable == null ? null : throwable instanceof IrisException irisException ? irisException.getTranslationKey() : null;
        var translationParams = throwable == null ? null : throwable instanceof IrisException irisException ? irisException.getTranslationParams() : null;

        return new IrisWebsocketDTO(type, message, errorMessage, errorTranslationKey, translationParams, rateLimitInfo, stages);
    }

    @Override
    public Map<String, Object> translationParams() {
        return translationParams != null ? Collections.unmodifiableMap(translationParams) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        IrisWebsocketDTO that = (IrisWebsocketDTO) o;
        return Objects.equals(message, that.message) && Objects.equals(errorMessage, that.errorMessage) && Objects.equals(errorTranslationKey, that.errorTranslationKey)
                && Objects.equals(stages, that.stages) && type == that.type && Objects.equals(translationParams, that.translationParams);
    }

    public enum IrisWebsocketMessageType {
        MESSAGE, STATUS, ERROR
    }
}
