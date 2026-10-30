package de.tum.cit.aet.artemis.core.service.agent;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.databind.JsonNode;

/**
 * The REST API of this server as the tools of the MCP endpoint see it.
 * <p>
 * A tool does its work by calling the same endpoints an agent could call itself, with the token of the request it is serving. The
 * role checks, the scopes, the validation and the draft rules therefore apply to a tool exactly as to a direct call, and there is
 * no second security model to keep in step with the first.
 */
public interface AgentApi {

    /**
     * Calls an endpoint with a JSON body.
     *
     * @param method the HTTP method
     * @param path   the path with its query, starting with {@code /api/}
     * @param body   the JSON body, or null for none
     * @return the status and the body of the response
     */
    AgentApiResponse send(HttpMethod method, String path, @Nullable JsonNode body);

    /**
     * Calls an endpoint with a plain text body.
     *
     * @param method the HTTP method
     * @param path   the path with its query, starting with {@code /api/}
     * @param text   the text body
     * @return the status and the body of the response
     */
    AgentApiResponse sendText(HttpMethod method, String path, String text);

    /**
     * The Authorization header of the request being served, which carries the personal access token a call is made with.
     *
     * @return the header value
     */
    default String authorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null) {
                return header;
            }
        }
        throw new IllegalStateException("There is no request with an Authorization header to act for");
    }
}
