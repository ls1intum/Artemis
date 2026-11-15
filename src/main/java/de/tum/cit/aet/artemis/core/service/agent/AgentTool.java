package de.tum.cit.aet.artemis.core.service.agent;

import java.util.Set;

import de.tum.cit.aet.artemis.core.dto.agent.JsonSchemaDTO;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;

/**
 * A tool of the MCP endpoint: a task an agent can ask for, described by a name, a description and a JSON schema of its input.
 *
 * @param name          the name the agent calls it by
 * @param description   what it does and when to use it
 * @param anyOfScopes   the scopes of which a token needs at least one to see the tool; the call itself is authorized by the endpoints it
 *                          uses
 * @param readOnly      whether the tool changes nothing
 * @param inputSchema   the schema of the arguments, as the agent is told
 * @param argumentsType the type the arguments are read as
 * @param handler       what the tool does
 * @param <A>           the arguments of the tool
 */
public record AgentTool<A>(String name, String description, Set<AgentScope> anyOfScopes, boolean readOnly, JsonSchemaDTO inputSchema, Class<A> argumentsType,
        AgentToolHandler<A> handler) {

    /**
     * What a tool does with its arguments.
     *
     * @param <A> the arguments of the tool
     */
    @FunctionalInterface
    public interface AgentToolHandler<A> {

        /**
         * Runs the tool.
         *
         * @param arguments the arguments the agent sent
         * @param api       the REST API to call, acting with the token of the request
         * @return what to tell the agent
         * @throws IllegalArgumentException if an argument is missing or malformed, which is reported to the agent as a failure
         */
        AgentToolResult call(A arguments, AgentApi api);
    }
}
