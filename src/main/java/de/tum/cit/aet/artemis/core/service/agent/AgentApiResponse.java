package de.tum.cit.aet.artemis.core.service.agent;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.dto.agent.AgentProblemDTO;

/**
 * The answer of the REST API to a call of a tool.
 *
 * @param status  the HTTP status
 * @param body    the body as text, or null if there was none
 * @param problem the problem document, if the call failed with one
 */
public record AgentApiResponse(int status, @Nullable String body, @Nullable AgentProblemDTO problem) {

    /**
     * Reads the answer of an endpoint. The body of a failure is read as a problem document where it is one.
     *
     * @param jsonMapper the mapper to read the problem document with
     * @param status     the HTTP status
     * @param body       the body as text, blank if there was none
     * @return the answer
     */
    public static AgentApiResponse of(JsonMapper jsonMapper, int status, String body) {
        String text = body.isBlank() ? null : body;
        AgentProblemDTO problem = null;
        if (text != null && (status < 200 || status >= 300)) {
            try {
                problem = jsonMapper.readValue(text, AgentProblemDTO.class);
            }
            catch (JacksonException e) {
                // Not a problem document, for example the page of a proxy: the text is reported as it is.
            }
        }
        return new AgentApiResponse(status, text, problem);
    }

    /**
     * Whether the call succeeded.
     *
     * @return true for a status of the 2xx class
     */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
