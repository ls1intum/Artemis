package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A JSON-RPC 2.0 answer: either a result or an error.
 *
 * @param jsonrpc the protocol version, always {@code 2.0}
 * @param id      the id of the request it answers; null in an error about a request that could not be read, and written then too, as the
 *                    protocol asks
 * @param result  the result of a successful call
 * @param error   what went wrong
 * @param <R>     the kind of result
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JsonRpcResponseDTO<R>(String jsonrpc, @JsonInclude() JsonRpcIdDTO id, R result, JsonRpcErrorDTO error) {

    private static final String VERSION = "2.0";

    /**
     * The answer to a call that succeeded.
     *
     * @param id     the id of the request
     * @param result the result
     * @param <R>    the kind of result
     * @return the answer
     */
    public static <R> JsonRpcResponseDTO<R> success(JsonRpcIdDTO id, R result) {
        return new JsonRpcResponseDTO<>(VERSION, id, result, null);
    }

    /**
     * The answer to a call that failed.
     *
     * @param id      the id of the request, or null if it could not be read
     * @param code    the JSON-RPC error code
     * @param message what went wrong
     * @return the answer
     */
    public static JsonRpcResponseDTO<Void> failure(JsonRpcIdDTO id, int code, String message) {
        return new JsonRpcResponseDTO<>(VERSION, id, null, new JsonRpcErrorDTO(code, message));
    }
}
