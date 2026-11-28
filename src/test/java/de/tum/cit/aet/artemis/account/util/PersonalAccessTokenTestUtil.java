package de.tum.cit.aet.artemis.account.util;

import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.account.domain.PersonalAccessToken;
import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Stores personal access tokens with a known secret, so that tests can call the REST API with them.
 */
public final class PersonalAccessTokenTestUtil {

    private PersonalAccessTokenTestUtil() {
    }

    /**
     * A stored token together with its secret, which the database does not keep.
     */
    public record SavedToken(PersonalAccessToken token, String secret) {

        /**
         * @return the value of the Authorization header that authenticates with this token
         */
        public String bearer() {
            return "Bearer " + secret;
        }
    }

    /**
     * Stores a token.
     *
     * @param repository where tokens are stored
     * @param userId     the owner
     * @param scopes     the scopes the token holds
     * @param lifetime   how long the token is valid from now; negative for one that has already expired
     * @return the stored token and its secret
     */
    public static SavedToken saveToken(PersonalAccessTokenRepository repository, long userId, Set<AgentScope> scopes, Duration lifetime) {
        String secret = PERSONAL_ACCESS_TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        String joinedScopes = scopes.stream().map(AgentScope::getValue).collect(Collectors.joining(","));
        var token = new PersonalAccessToken(userId, "test token", secret.substring(0, PERSONAL_ACCESS_TOKEN_PREFIX.length() + 4), sha256(secret), joinedScopes, now,
                now.plus(lifetime));
        return new SavedToken(repository.save(token), secret);
    }

    /** The revision of the Model Context Protocol that the MCP endpoint speaks. */
    public static final String MCP_PROTOCOL_VERSION = "2026-07-28";

    /**
     * Builds a request to the MCP endpoint as a client of the current protocol revision sends it: with the version and the capabilities of the
     * client in {@code _meta}, and the headers that mirror the method and the tool name. A test changes what it wants to break.
     *
     * @param method the JSON-RPC method, such as {@code tools/call}
     * @param params what the method needs, without {@code _meta}
     * @return the request, without authentication
     */
    public static MockHttpServletRequestBuilder mcpRequest(String method, Map<String, Object> params) {
        Map<String, Object> withMeta = new HashMap<>(params);
        withMeta.put("_meta", Map.of("io.modelcontextprotocol/protocolVersion", MCP_PROTOCOL_VERSION, "io.modelcontextprotocol/clientCapabilities", Map.of()));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post("/api/core/agent/mcp").contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION).header("Mcp-Method", method);
        if (params.get("name") instanceof String name) {
            builder.header("Mcp-Name", name);
        }
        try {
            return builder.content(JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", withMeta)));
        }
        catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Replaces a header of a request, where {@code header()} of the builder would add a second value to the one it already has.
     *
     * @param builder the request
     * @param name    the header
     * @param value   its new value
     * @return the request
     */
    public static MockHttpServletRequestBuilder replacingHeader(MockHttpServletRequestBuilder builder, String name, String value) {
        return builder.with(servletRequest -> {
            if (servletRequest instanceof MockHttpServletRequest mock) {
                mock.removeHeader(name);
            }
            servletRequest.addHeader(name, value);
            return servletRequest;
        });
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
