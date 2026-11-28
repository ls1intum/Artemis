package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error of a failed JSON-RPC call.
 *
 * @param code    the JSON-RPC error code
 * @param message what went wrong
 * @param data    what a client needs to react, if the error defines it, such as the supported versions
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JsonRpcErrorDTO(int code, String message, Object data) {
}
