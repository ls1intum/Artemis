package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.ExternalLoginProperties;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginCodeRequestDTO;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginCodeResponseDTO;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginTokenRequestDTO;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginTokenResponseDTO;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.externallogin.PkceUtil;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for the external-client browser login handoff: the authenticated code-issue endpoint
 * ({@link de.tum.cit.aet.artemis.core.web.ExternalLoginResource}) and the public code/PKCE exchange endpoint
 * ({@link de.tum.cit.aet.artemis.core.web.open.PublicExternalLoginResource}).
 * <p>
 * Runs single-threaded because the feature flag is toggled on the shared {@link ExternalLoginProperties} bean.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ExternalLoginResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "externallogin";

    private static final String USERNAME = TEST_PREFIX + "student1";

    private static final String CODE_ENDPOINT = "/api/core/external-login/code";

    private static final String TOKEN_ENDPOINT = "/api/core/public/external-login/token";

    private static final String CALLBACK = "vscode://aet-tum.iris-thaumantias/auth-complete";

    // A PKCE verifier (RFC 7636 allows 43-128 unreserved characters) and its S256 challenge.
    private static final String VERIFIER = "verifier-with-enough-entropy-0123456789-abcdefghij";

    private static final String CHALLENGE = PkceUtil.s256Challenge(VERIFIER);

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private ExternalLoginProperties externalLoginProperties;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    // --- feature flag toggling -------------------------------------------------------------------

    /**
     * Temporarily enables the (default-off) feature by swapping the allowlists on the shared properties bean,
     * runs the test, and restores the previous values afterwards. The resource reads the properties per request,
     * so no Spring context restart is required (which would also violate the context-configuration architecture rules).
     */
    private void withFeatureEnabled(List<String> schemes, List<String> authorities, Executable test) throws Throwable {
        Object previousSchemes = ReflectionTestUtils.getField(externalLoginProperties, "allowedRedirectSchemes");
        Object previousAuthorities = ReflectionTestUtils.getField(externalLoginProperties, "allowedRedirectAuthorities");
        ReflectionTestUtils.setField(externalLoginProperties, "allowedRedirectSchemes", schemes);
        ReflectionTestUtils.setField(externalLoginProperties, "allowedRedirectAuthorities", authorities);
        try {
            test.execute();
        }
        finally {
            ReflectionTestUtils.setField(externalLoginProperties, "allowedRedirectSchemes", previousSchemes);
            ReflectionTestUtils.setField(externalLoginProperties, "allowedRedirectAuthorities", previousAuthorities);
        }
    }

    private void withFeatureEnabled(Executable test) throws Throwable {
        withFeatureEnabled(List.of("vscode", "vscode-insiders"), List.of("aet-tum.iris-thaumantias"), test);
    }

    // --- request helpers -------------------------------------------------------------------------

    private String studentSessionJwt() {
        var authentication = new UsernamePasswordAuthenticationToken(USERNAME, USERNAME, List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        return tokenProvider.createToken(authentication, 24 * 60 * 60 * 1000, null);
    }

    private MvcResult issue(ExternalLoginCodeRequestDTO body, String sessionJwt, HttpStatus expectedStatus) throws Exception {
        var builder = post(new URI(CODE_ENDPOINT)).contentType(MediaType.APPLICATION_JSON).content(request.getObjectMapper().writeValueAsString(body));
        if (sessionJwt != null) {
            builder = builder.cookie(new Cookie(Constants.JWT_COOKIE_NAME, sessionJwt));
        }
        return request.performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
    }

    private String issueCodeSuccessfully(String callback) throws Exception {
        MvcResult result = issue(new ExternalLoginCodeRequestDTO(CHALLENGE, callback), studentSessionJwt(), HttpStatus.OK);
        ExternalLoginCodeResponseDTO response = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), ExternalLoginCodeResponseDTO.class);
        assertThat(response.code()).isNotBlank();
        return response.code();
    }

    private ExternalLoginTokenResponseDTO exchange(String code, String verifier, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody(TOKEN_ENDPOINT, new ExternalLoginTokenRequestDTO(code, verifier), ExternalLoginTokenResponseDTO.class, expectedStatus);
    }

    // --- happy path ------------------------------------------------------------------------------

    @Test
    void shouldIssueAndExchangeCodeForAValidJwt() throws Throwable {
        withFeatureEnabled(() -> {
            String code = issueCodeSuccessfully(CALLBACK);
            ExternalLoginTokenResponseDTO token = exchange(code, VERIFIER, HttpStatus.OK);

            assertThat(token.accessToken()).isNotBlank();
            // The exchanged token is a valid Artemis JWT minted for the originating principal.
            assertThat(tokenProvider.validateTokenForAuthority(token.accessToken(), null)).isTrue();
            var authentication = tokenProvider.getAuthentication(token.accessToken());
            assertThat(authentication.getName()).isEqualTo(USERNAME);
            assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)).contains(Role.STUDENT.getAuthority());
        });
    }

    @Test
    void shouldCapMintedTokenToOriginatingSessionLifetime() throws Throwable {
        withFeatureEnabled(() -> {
            // A source session JWT that expires well before the default JWT validity, so the cap is observable.
            long now = System.currentTimeMillis();
            Date sessionExpiry = new Date(now + 10 * 60 * 1000); // 10 minutes
            var authentication = new UsernamePasswordAuthenticationToken(USERNAME, USERNAME, List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
            String shortLivedSessionJwt = tokenProvider.createToken(authentication, new Date(now), sessionExpiry, null, null);

            MvcResult result = issue(new ExternalLoginCodeRequestDTO(CHALLENGE, CALLBACK), shortLivedSessionJwt, HttpStatus.OK);
            ExternalLoginCodeResponseDTO codeResponse = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), ExternalLoginCodeResponseDTO.class);
            ExternalLoginTokenResponseDTO token = exchange(codeResponse.code(), VERIFIER, HttpStatus.OK);

            // The minted token must not outlive the originating session: capped to its remaining validity, not the default JWT validity.
            Date mintedExpiry = tokenProvider.getExpirationDate(token.accessToken());
            assertThat(mintedExpiry).isBeforeOrEqualTo(new Date(sessionExpiry.getTime() + 5000));
            assertThat(mintedExpiry).isAfter(new Date(now + 5 * 60 * 1000));
        });
    }

    @Test
    void shouldPreservePasskeyClaimsThroughExchange() throws Throwable {
        withFeatureEnabled(() -> {
            // A source session JWT representing an approved passkey login (auth-method PASSKEY, super-admin approved).
            long now = System.currentTimeMillis();
            var authentication = new UsernamePasswordAuthenticationToken(USERNAME, USERNAME, List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
            authentication.setDetails(Map.of(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, true));
            String passkeySessionJwt = tokenProvider.createToken(authentication, new Date(now), new Date(now + 24 * 60 * 60 * 1000), null, true);
            // Sanity: the source token carries the passkey claims.
            assertThat(tokenProvider.getAuthenticationMethod(passkeySessionJwt)).isEqualTo(AuthenticationMethod.PASSKEY);
            assertThat(tokenProvider.isPasskeySuperAdminApproved(passkeySessionJwt)).isTrue();

            MvcResult result = issue(new ExternalLoginCodeRequestDTO(CHALLENGE, CALLBACK), passkeySessionJwt, HttpStatus.OK);
            ExternalLoginCodeResponseDTO codeResponse = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), ExternalLoginCodeResponseDTO.class);
            ExternalLoginTokenResponseDTO token = exchange(codeResponse.code(), VERIFIER, HttpStatus.OK);

            // The exchanged handoff token must not degrade to a password token: it preserves the originating session's
            // auth method and passkey-approval, so passkey-approved admins keep working with the returned JWT.
            assertThat(tokenProvider.getAuthenticationMethod(token.accessToken())).isEqualTo(AuthenticationMethod.PASSKEY);
            assertThat(tokenProvider.isPasskeySuperAdminApproved(token.accessToken())).isTrue();
        });
    }

    // --- exchange security -----------------------------------------------------------------------

    @Test
    void shouldRejectExchangeWithWrongVerifier() throws Throwable {
        withFeatureEnabled(() -> {
            String code = issueCodeSuccessfully(CALLBACK);
            assertThat(exchange(code, "a-completely-different-verifier-value-987654321", HttpStatus.UNAUTHORIZED)).isNull();
        });
    }

    @Test
    void shouldBurnCodeEvenWhenVerifierIsWrong() throws Throwable {
        withFeatureEnabled(() -> {
            String code = issueCodeSuccessfully(CALLBACK);
            // A failed exchange with a well-formed but wrong verifier must still consume the single-use code (consume-before-verify),
            assertThat(exchange(code, "a-wrong-verifier-value-1122334455667788-abcdefgh", HttpStatus.UNAUTHORIZED)).isNull();
            // so the same code cannot subsequently be redeemed even with the correct verifier (no brute-force on one code).
            assertThat(exchange(code, VERIFIER, HttpStatus.UNAUTHORIZED)).isNull();
        });
    }

    @Test
    void shouldRejectReusingACodeASecondTime() throws Throwable {
        withFeatureEnabled(() -> {
            String code = issueCodeSuccessfully(CALLBACK);
            exchange(code, VERIFIER, HttpStatus.OK);
            // single-use: the same code cannot be redeemed again
            assertThat(exchange(code, VERIFIER, HttpStatus.UNAUTHORIZED)).isNull();
        });
    }

    @Test
    void shouldRejectUnknownCode() throws Exception {
        // The public exchange endpoint does not depend on the feature flag.
        assertThat(exchange("a-code-that-was-never-issued", VERIFIER, HttpStatus.UNAUTHORIZED)).isNull();
    }

    @Test
    void shouldRejectExchangeWithMissingFields() throws Exception {
        assertThat(exchange(null, VERIFIER, HttpStatus.UNAUTHORIZED)).isNull();
        assertThat(exchange("some-code", null, HttpStatus.UNAUTHORIZED)).isNull();
    }

    @Test
    void shouldRejectMalformedVerifier() throws Throwable {
        withFeatureEnabled(() -> {
            String code = issueCodeSuccessfully(CALLBACK);
            // A verifier shorter than the RFC 7636 minimum (43 chars) is malformed and rejected before any code work.
            assertThat(exchange(code, "too-short-verifier", HttpStatus.UNAUTHORIZED)).isNull();
        });
    }

    // --- issue endpoint guards -------------------------------------------------------------------

    @Test
    void shouldReturnNotFoundWhenFeatureDisabled() throws Exception {
        // The feature is disabled by default (empty allowlist), so the endpoint behaves as if it does not exist.
        issue(new ExternalLoginCodeRequestDTO(CHALLENGE, CALLBACK), studentSessionJwt(), HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnNotFoundWhenSchemesSetButAuthoritiesEmpty() throws Throwable {
        // A scheme allowlist without an authority allowlist is treated as disabled (fail closed), so the endpoint 404s.
        withFeatureEnabled(List.of("vscode", "vscode-insiders"), List.of(),
                () -> issue(new ExternalLoginCodeRequestDTO(CHALLENGE, CALLBACK), studentSessionJwt(), HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldRejectBlankCodeChallenge() throws Throwable {
        withFeatureEnabled(() -> issue(new ExternalLoginCodeRequestDTO("  ", CALLBACK), studentSessionJwt(), HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldRejectMalformedCodeChallenge() throws Throwable {
        // A non-S256-shaped challenge (wrong length / illegal characters) is rejected before a JWT-bearing code is minted.
        withFeatureEnabled(() -> issue(new ExternalLoginCodeRequestDTO("not-a-valid-s256-challenge", CALLBACK), studentSessionJwt(), HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldRejectHttpCallback() throws Throwable {
        withFeatureEnabled(() -> issue(new ExternalLoginCodeRequestDTO(CHALLENGE, "http://example.com/callback"), studentSessionJwt(), HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldRejectCallbackWithDisallowedScheme() throws Throwable {
        withFeatureEnabled(() -> issue(new ExternalLoginCodeRequestDTO(CHALLENGE, "evilapp://host/callback"), studentSessionJwt(), HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldRejectCallbackWithDisallowedAuthority() throws Throwable {
        withFeatureEnabled(List.of("vscode"), List.of("aet-tum.iris-thaumantias"),
                () -> issue(new ExternalLoginCodeRequestDTO(CHALLENGE, "vscode://some-other-extension/callback"), studentSessionJwt(), HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldRequireAuthenticationToIssueCode() throws Exception {
        // Without a session JWT the request is rejected by the security layer before the handler runs.
        issue(new ExternalLoginCodeRequestDTO(CHALLENGE, CALLBACK), null, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectToolScopedSessionJwt() throws Throwable {
        // A tool-scoped token must never be upgraded into a full (unscoped) Artemis JWT via the handoff. On this
        // non-public endpoint the ToolsInterceptor rejects tool tokens with 403 before the handler runs (the handler
        // also guards against it explicitly), so no code is ever minted.
        withFeatureEnabled(() -> {
            var authentication = new UsernamePasswordAuthenticationToken(USERNAME, USERNAME, List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
            String toolScopedJwt = tokenProvider.createToken(authentication, 24 * 60 * 60 * 1000, ToolTokenType.SCORPIO);
            issue(new ExternalLoginCodeRequestDTO(CHALLENGE, CALLBACK), toolScopedJwt, HttpStatus.FORBIDDEN);
        });
    }
}
