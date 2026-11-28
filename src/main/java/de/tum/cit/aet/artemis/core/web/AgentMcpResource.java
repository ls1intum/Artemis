package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;
import de.tum.cit.aet.artemis.core.dto.agent.JsonRpcResponseDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AllowedScopes;
import de.tum.cit.aet.artemis.core.security.allowedScopes.PersonalAccessTokenAuthentication;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.agent.AgentMcpAnswer;
import de.tum.cit.aet.artemis.core.service.agent.AgentMcpHeaders;
import de.tum.cit.aet.artemis.core.service.agent.AgentMcpService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * The Model Context Protocol endpoint of agent access, revision 2026-07-28. Present only when agent access is enabled.
 * <p>
 * It is reachable only with a personal access token. It needs no scope of its own: the token's scopes decide which tools it lists, and
 * the endpoint a tool calls decides whether the call is allowed. A GET or DELETE is answered with 405, as the protocol asks of a server
 * that does not stream and keeps no session.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@FeatureUsage(UserFeature.AGENT_ACCESS)
@RestController
@RequestMapping("api/core/")
public class AgentMcpResource {

    private final AgentMcpService agentMcpService;

    public AgentMcpResource(AgentMcpService agentMcpService) {
        this.agentMcpService = agentMcpService;
    }

    /**
     * POST /agent/mcp : handles one JSON-RPC message of the Model Context Protocol.
     *
     * @param message         the JSON-RPC message, as text: it is read by the service, so that a message that is not valid JSON-RPC is answered as a JSON-RPC error
     * @param origin          the page that sends the request, if a browser does
     * @param protocolVersion the {@code MCP-Protocol-Version} header, which has to match the message
     * @param method          the {@code Mcp-Method} header, which has to match the message
     * @param name            the {@code Mcp-Name} header, which a tool call has to carry
     * @return the JSON-RPC answer with the status the transport prescribes, 202 for a notification, or 403 for an origin that is not allowed
     */
    @Hidden
    @PostMapping(value = "agent/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    @AllowedScopes({})
    public ResponseEntity<JsonRpcResponseDTO<?>> handleMessage(@RequestBody String message, @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion, @RequestHeader(value = "Mcp-Method", required = false) String method,
            @RequestHeader(value = "Mcp-Name", required = false) String name) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof PersonalAccessTokenAuthentication token)) {
            throw new AccessForbiddenException("The MCP endpoint accepts only personal access tokens.");
        }
        if (!agentMcpService.isAllowedOrigin(origin)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        AgentMcpAnswer answer = agentMcpService.handle(message, new AgentMcpHeaders(protocolVersion, method, name), token.getScopes());
        return ResponseEntity.status(answer.status()).body(answer.body());
    }
}
