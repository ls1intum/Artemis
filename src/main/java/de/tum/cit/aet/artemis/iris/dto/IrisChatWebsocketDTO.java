package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * A DTO for sending status updates of Iris to the client via the websocket
 *
 * @param type          the type of the message
 * @param message       an IrisMessageResponseDTO instance if the type is MESSAGE
 * @param rateLimitInfo the rate limit information
 * @param runState      the current Pyris run state
 * @param error         optional Pyris status error
 * @param sessionTitle  the chat session title
 * @param suggestions   the suggestions to send to the client
 * @param tokens        the token usage information for the response
 * @param citationInfo  metadata about citations referenced in the response
 * @param runId         the id of the Pyris run that produced the payload
 * @param partialResult the current accumulated partial response text
 * @param partialSeq    the monotonic sequence number of the partial response
 * @param activities    current Pyris activity snapshot
 * @param activitySeq   monotonic sequence number for the activity snapshot
 * @param finalResult   whether a MESSAGE frame is the final answer for the run; false means intermediate
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatWebsocketDTO(IrisWebsocketMessageType type, IrisMessageResponseDTO message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo,
        @Nullable PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, String sessionTitle, List<String> suggestions, List<LLMRequest> tokens,
        List<IrisCitationMetaDTO> citationInfo, @Nullable String runId, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities,
        @Nullable Integer activitySeq, @JsonProperty("final") @Nullable Boolean finalResult) {

    /**
     * Creates a new IrisWebsocketDTO instance with the given parameters
     * Takes care of setting the type correctly
     *
     * @param message       the IrisMessageResponseDTO (optional)
     * @param rateLimitInfo the rate limit information
     * @param runState      the current Pyris run state
     * @param error         optional Pyris status error
     * @param sessionTitle  the session title to send
     * @param suggestions   the suggestions to send
     * @param tokens        the token usage information to send
     * @param citationInfo  metadata about citations referenced in the response
     */
    public IrisChatWebsocketDTO(@Nullable IrisMessageResponseDTO message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, @Nullable PyrisRunState runState,
            @Nullable PyrisStatusErrorDTO error, String sessionTitle, List<String> suggestions, List<LLMRequest> tokens, List<IrisCitationMetaDTO> citationInfo) {
        this(message, rateLimitInfo, runState, error, sessionTitle, suggestions, tokens, citationInfo, null, null, null, null, null, null);
    }

    /**
     * Creates a new IrisWebsocketDTO instance with the given parameters
     * Takes care of setting the type correctly.
     *
     * @param message       the IrisMessageResponseDTO (optional)
     * @param rateLimitInfo the rate limit information
     * @param runState      the current Pyris run state
     * @param error         optional Pyris status error
     * @param sessionTitle  the session title to send
     * @param suggestions   the suggestions to send
     * @param tokens        the token usage information to send
     * @param citationInfo  metadata about citations referenced in the response
     * @param runId         the id of the Pyris run that produced the payload
     * @param partialResult the current accumulated partial response text
     * @param partialSeq    the monotonic sequence number of the partial response
     * @param activities    current Pyris activity snapshot
     * @param activitySeq   monotonic sequence number for the activity snapshot
     */
    public IrisChatWebsocketDTO(@Nullable IrisMessageResponseDTO message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, @Nullable PyrisRunState runState,
            @Nullable PyrisStatusErrorDTO error, String sessionTitle, List<String> suggestions, List<LLMRequest> tokens, List<IrisCitationMetaDTO> citationInfo,
            @Nullable String runId, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq) {
        this(message, rateLimitInfo, runState, error, sessionTitle, suggestions, tokens, citationInfo, runId, partialResult, partialSeq, activities, activitySeq, null);
    }

    /**
     * Creates a new IrisWebsocketDTO instance with the given parameters
     * Takes care of setting the type correctly.
     *
     * @param message       the IrisMessageResponseDTO (optional)
     * @param rateLimitInfo the rate limit information
     * @param runState      the current Pyris run state
     * @param error         optional Pyris status error
     * @param sessionTitle  the session title to send
     * @param suggestions   the suggestions to send
     * @param tokens        the token usage information to send
     * @param citationInfo  metadata about citations referenced in the response
     * @param runId         the id of the Pyris run that produced the payload
     * @param partialResult the current accumulated partial response text
     * @param partialSeq    the monotonic sequence number of the partial response
     * @param activities    current Pyris activity snapshot
     * @param activitySeq   monotonic sequence number for the activity snapshot
     * @param finalResult   whether a MESSAGE frame is the final answer for the run; false means intermediate
     */
    public IrisChatWebsocketDTO(@Nullable IrisMessageResponseDTO message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, @Nullable PyrisRunState runState,
            @Nullable PyrisStatusErrorDTO error, String sessionTitle, List<String> suggestions, List<LLMRequest> tokens, List<IrisCitationMetaDTO> citationInfo,
            @Nullable String runId, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq,
            @Nullable Boolean finalResult) {
        this(determineType(message, partialResult), message, rateLimitInfo, runState, error, sessionTitle, suggestions, tokens, citationInfo, runId, partialResult, partialSeq,
                activities, activitySeq, finalResult);
    }

    /**
     * Determines the type of WebSocket message based on the presence of a partial result or message.
     * <p>
     * Returns {@link IrisWebsocketMessageType#PARTIAL} if the partial result is not null,
     * {@link IrisWebsocketMessageType#MESSAGE} if the message is not null,
     * {@link IrisWebsocketMessageType#STATUS} otherwise.
     *
     * @param message       The message DTO associated with the WebSocket, which may be null.
     * @param partialResult The partial response text associated with the WebSocket, which may be null.
     * @return The {@link IrisWebsocketMessageType} indicating the type of the message based on the given parameters.
     */
    private static IrisWebsocketMessageType determineType(@Nullable IrisMessageResponseDTO message, @Nullable String partialResult) {
        if (partialResult != null) {
            return IrisWebsocketMessageType.PARTIAL;
        }
        return message != null ? IrisWebsocketMessageType.MESSAGE : IrisWebsocketMessageType.STATUS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IrisChatWebsocketDTO that = (IrisChatWebsocketDTO) o;
        return type == that.type && Objects.equals(message, that.message) && Objects.equals(rateLimitInfo, that.rateLimitInfo) && runState == that.runState
                && Objects.equals(error, that.error) && Objects.equals(sessionTitle, that.sessionTitle) && Objects.equals(suggestions, that.suggestions)
                && Objects.equals(tokens, that.tokens) && Objects.equals(citationInfo, that.citationInfo) && Objects.equals(runId, that.runId)
                && Objects.equals(partialResult, that.partialResult) && Objects.equals(partialSeq, that.partialSeq) && Objects.equals(activities, that.activities)
                && Objects.equals(activitySeq, that.activitySeq) && Objects.equals(finalResult, that.finalResult);
    }

    public enum IrisWebsocketMessageType {
        MESSAGE, STATUS, PARTIAL
    }
}
