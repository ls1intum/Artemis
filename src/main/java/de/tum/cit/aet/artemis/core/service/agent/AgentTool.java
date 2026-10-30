package de.tum.cit.aet.artemis.core.service.agent;

import java.util.Set;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;

/**
 * A tool of the MCP endpoint: a task an agent can ask for, described by a name, a description and a JSON schema of its input.
 *
 * @param name        the name the agent calls it by
 * @param description what it does and when to use it
 * @param anyOfScopes the scopes of which a token needs at least one to see the tool; the call itself is authorized by the endpoints it
 *                        uses
 * @param readOnly    whether the tool changes nothing
 * @param inputSchema the JSON schema of the arguments
 * @param handler     what the tool does
 */
public record AgentTool(String name, String description, Set<AgentScope> anyOfScopes, boolean readOnly, JsonNode inputSchema, AgentToolHandler handler) {

    /**
     * What a tool does with its arguments.
     */
    @FunctionalInterface
    public interface AgentToolHandler {

        /**
         * Runs the tool.
         *
         * @param arguments the arguments the agent sent
         * @param api       the REST API to call, acting with the token of the request
         * @return what to tell the agent
         * @throws IllegalArgumentException if an argument is missing or malformed, which is reported to the agent as a failure
         */
        AgentToolResult call(JsonNode arguments, AgentApi api);
    }
}
