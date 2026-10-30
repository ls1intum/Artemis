package de.tum.cit.aet.artemis.core.service.agent;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;

/**
 * The answer of the REST API to a call of a tool.
 *
 * @param status the HTTP status
 * @param body   the body as JSON, or a text node if it was not JSON, or null if there was none
 */
public record AgentApiResponse(int status, @Nullable JsonNode body) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
