package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.BEARER_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.JWT_COOKIE_NAME;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotAuthorizedException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.WebUtils;

import de.tum.cit.aet.artemis.core.service.PasskeyTokenRenewalService;
import io.jsonwebtoken.Claims;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is found.
 */
public class JWTFilter extends GenericFilterBean {

    private static final Logger log = LoggerFactory.getLogger(JWTFilter.class);

    private final TokenProvider tokenProvider;

    private final JWTCookieService jwtCookieService;

    private final PasskeyTokenRenewalService passkeyTokenRenewalService;

    /** The longest a "remember me" session may live, measured from the original login, however often it is extended. */
    private final long maxSessionLifetimeInSeconds;

    /**
     * Tokens whose renewal was refused, keyed by a digest of the signed token and valued with the token's expiry.
     * <p>
     * A refusal does not change the cookie, so the token stays rotation-due and every later request would repeat the
     * lookups behind that refusal - for a session refused early in its second half, that is per-request database load for
     * as long as the token lives. Remembering the refusal keeps it at one lookup per token, which is what the comment
     * below claims. Only refusals are remembered: an approval rotates the cookie, so the next request carries a different
     * token and asks again.
     * <p>
     * Caching until the token expires is safe because every reason to refuse is terminal for that token - the passkey is
     * gone, or the account was deactivated, deleted or had its credentials changed. None of them can be undone in a way
     * that should revive this session rather than require a fresh sign-in.
     * <p>
     * A digest rather than the token itself, so a heap dump does not hand out session credentials that would otherwise
     * not be retained. Bounded, and swept of expired entries when it fills, so a node cannot accumulate them.
     */
    private final Map<String, Long> refusedRenewals = new ConcurrentHashMap<>();

    /** Upper bound for {@link #refusedRenewals}. Far above the number of sessions one node can have refused at once. */
    private static final int MAX_REFUSED_RENEWALS = 10_000;

    private final long tokenValidityInSecondsForPasskey;

    public JWTFilter(TokenProvider tokenProvider, JWTCookieService jwtCookieService, long tokenValidityInSecondsForPasskey, PasskeyTokenRenewalService passkeyTokenRenewalService,
            long maxSessionLifetimeInSeconds) {
        this.tokenProvider = tokenProvider;
        this.jwtCookieService = jwtCookieService;
        this.passkeyTokenRenewalService = passkeyTokenRenewalService;
        this.maxSessionLifetimeInSeconds = maxSessionLifetimeInSeconds;
        this.tokenValidityInSecondsForPasskey = tokenValidityInSecondsForPasskey;
    }

    /**
     * Renews the JWT token if its remaining lifetime is less than 50% of its total validity period.
     * The renewed token retains the original issuedAt timestamp.
     *
     * <p>
     * <b>Configurable values:</b>
     * </p>
     * <ul>
     * <li>{@code artemis.user-management.passkey.token-validity-in-seconds-for-passkey}: Caps the maximum lifetime of a renewed token, limiting how long an infrequently used
     * token can remain valid.</li>
     * <li>{@code jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me}: Determines the expiration time for authentication tokens.</li>
     * </ul>
     *
     * <p>
     * <b>Security:</b>
     * </p>
     * <p>
     * This mechanism relies on secure cookie storage and HTTPS. An issued token is still not revocable on demand - it is
     * validated from its claims alone - but a passkey session is no longer extended once the passkey it was issued for has
     * been deleted, so deleting a passkey bounds the session to its current token. Role changes are not checked, as
     * resources for non-admin roles will validate the current role during access.
     * </p>
     *
     * @param jwtToken       The current JWT token to evaluate for renewal.
     * @param authentication The {@link org.springframework.security.core.Authentication} object associated with the token.
     * @param response       The {@link jakarta.servlet.http.HttpServletResponse} where the renewed token will be added as a cookie.
     * @throws NotAuthorizedException If the token cannot be renewed due to validation or other issues.
     */
    private void rotateTokenSilently(String jwtToken, Authentication authentication, HttpServletResponse response) throws NotAuthorizedException {
        // Parsed once for every claim below. Each String accessor on TokenProvider verifies the signature again, and this
        // method runs on every authenticated cookie request, so reading the claims through them cost one verification each
        // even for a session that is not due for rotation.
        Claims claims = this.tokenProvider.parseClaims(jwtToken);

        // Both kinds of session are extended while they stay in use, up to a ceiling measured from the original login: the
        // passkey lifetime for a passkey session, max-session-lifetime-in-seconds for a "remember me" one. A plain session
        // is never extended - it is meant to be short.
        boolean isPasskeySession = Objects.equals(this.tokenProvider.getAuthenticationMethod(claims), AuthenticationMethod.PASSKEY);
        boolean isRememberMeSession = this.tokenProvider.isRememberMeSession(claims);
        if (!isPasskeySession && !isRememberMeSession) {
            return;
        }

        // Extract issued and expiration timestamps from the existing token
        Date issuedAt = claims.getIssuedAt();
        Date expirationDate = claims.getExpiration();

        // Calculate remaining lifetime of the token
        long nowInMs = System.currentTimeMillis();
        long tokenValidityInMs = this.tokenProvider.getTokenValidity(true);
        long remainingLifetime = expirationDate.getTime() - nowInMs;

        // Trigger rotation if token has less than half of its validity period remaining
        boolean isRemainingLifetimeBelowHalf = remainingLifetime < tokenValidityInMs / 2;
        if (isRemainingLifetimeBelowHalf) {
            // A rotation is the one moment in a session's life where a database lookup is affordable - it happens once per
            // rotation interval, not per request - so it is where everything that cannot reach an issued token is
            // re-checked: the passkey still exists, the account is still active, and its credentials have not changed since
            // the session started.
            // Asked before the lookups, so a token already refused costs nothing further.
            if (isRenewalAlreadyRefused(jwtToken, nowInMs)) {
                return;
            }
            String passkeyCredentialId = this.tokenProvider.getPasskeyCredentialId(claims);
            // Only a passkey session has a passkey to verify. Asking for a password session too would refuse every
            // "remember me" extension on an installation that does not have passkeys enabled.
            if (isPasskeySession && !passkeyTokenRenewalService.mayExtendPasskeySession(passkeyCredentialId)) {
                rememberRefusedRenewal(jwtToken, expirationDate.getTime());
                return;
            }
            if (!passkeyTokenRenewalService.mayExtendSessionForAccount(authentication.getName(), issuedAt.toInstant())) {
                rememberRefusedRenewal(jwtToken, expirationDate.getTime());
                return;
            }

            // Neither kind of session may outlive its ceiling, measured from the original login: the passkey lifetime for a
            // passkey session, and for a password session the lifetime a single non-rotating token used to have. This is the
            // only bound, deliberately - counting extensions instead would make the maximum depend on when the rotating
            // requests arrive. It is also all that bounds an externally managed account, where a password reset or a
            // deactivation done in LDAP, SAML or OIDC leaves no trace in the local fields the checks above read.
            long sessionCeilingInSeconds = isPasskeySession ? this.tokenValidityInSecondsForPasskey : this.maxSessionLifetimeInSeconds;
            long newTokenExpirationTimeInMs = Math.min(nowInMs + tokenValidityInMs, issuedAt.getTime() + Math.multiplyExact(sessionCeilingInSeconds, 1000));
            if (newTokenExpirationTimeInMs <= nowInMs) {
                // The ceiling has been reached, so there is no window left to hand out.
                return;
            }
            // Determine the lifetime of the rotated token
            long rotatedTokenDurationInMs = newTokenExpirationTimeInMs - nowInMs;
            // Create the rotated token with updated expiration and same issued time/tools
            // The passkey claims are read from the expiring token and passed on explicitly: the authentication was rebuilt
            // from that token and carries no details, so deriving them from it would drop the credential id (defeating the
            // check above from the second rotation onwards) and reset the super-admin approval flag.
            var rotatedToken = this.tokenProvider.createToken(authentication, issuedAt, new Date(newTokenExpirationTimeInMs), this.tokenProvider.getTools(claims), isPasskeySession,
                    passkeyCredentialId, this.tokenProvider.isPasskeySuperAdminApproved(claims), isRememberMeSession);

            // Build and set the new token as a response cookie
            ResponseCookie responseCookie = jwtCookieService.buildRotatedCookie(rotatedToken, rotatedTokenDurationInMs);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        }
    }

    /**
     * Whether this exact token has already been refused an extension, so the lookups behind that decision can be skipped.
     *
     * @param jwtToken the signed token presented by the request
     * @param nowInMs  current time, passed in so one request reads a single clock value
     * @return {@code true} if a refusal for this token is still remembered
     */
    private boolean isRenewalAlreadyRefused(String jwtToken, long nowInMs) {
        Long refusedUntil = refusedRenewals.get(digestOf(jwtToken));
        if (refusedUntil == null) {
            return false;
        }
        if (refusedUntil > nowInMs) {
            return true;
        }
        // The token outlived the refusal only if the clock moved past its expiry, in which case it is no longer usable
        // anyway. Drop the entry so it does not sit here until the next sweep.
        refusedRenewals.remove(digestOf(jwtToken), refusedUntil);
        return false;
    }

    /**
     * Remembers that this token may not be extended, until it expires.
     *
     * @param jwtToken      the signed token that was refused
     * @param expiresAtInMs when the token expires, which is when remembering it stops being useful
     */
    private void rememberRefusedRenewal(String jwtToken, long expiresAtInMs) {
        if (refusedRenewals.size() >= MAX_REFUSED_RENEWALS) {
            // Sweep instead of evicting arbitrarily: entries are only useful until their token expires, so in practice
            // this clears most of the map. Bounded work, and only on the rare request that finds the map full.
            long nowInMs = System.currentTimeMillis();
            refusedRenewals.values().removeIf(refusedUntil -> refusedUntil <= nowInMs);
            if (refusedRenewals.size() >= MAX_REFUSED_RENEWALS) {
                // Still full of live entries: skip caching rather than grow without bound. The lookups then repeat, which
                // is the behaviour this cache improves on, not one it breaks.
                return;
            }
        }
        refusedRenewals.put(digestOf(jwtToken), expiresAtInMs);
    }

    /**
     * Hashes a token so the cache key cannot be replayed if it is ever read out of memory.
     *
     * @param jwtToken the signed token
     * @return a hex SHA-256 digest of the token
     */
    private static String digestOf(String jwtToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(jwtToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE, so this cannot happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Filter that extracts a JWT from the incoming HTTP request, performs authentication,
     * optionally rotates the token if it meets security criteria, and sets the authentication
     * context for downstream processing.
     *
     * <p>
     * If a valid token is extracted from a cookie and does not contain tool metadata,
     * it is silently rotated and re-issued via a response cookie to extend the session securely.
     * </p>
     *
     * @param servletRequest  the incoming request
     * @param servletResponse the outgoing response
     * @param filterChain     the remaining filter chain
     * @throws IOException      in case of I/O errors
     * @throws ServletException in case of filter chain issues
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String jwtToken = null;
        String source = null;
        try {
            // Extract a valid JWT (if any) and track its source (e.g., header, cookie)
            JwtWithSource jwtWithSource = extractValidJwt(httpServletRequest, this.tokenProvider);
            if (jwtWithSource != null) {
                jwtToken = jwtWithSource.jwt();
                source = jwtWithSource.source();
            }
        }
        catch (IllegalArgumentException e) {
            // Send a 400 response if the JWT is malformed
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (jwtToken != null) {
            // Resolve the Spring Security authentication from the JWT
            Authentication authentication = this.tokenProvider.getAuthentication(jwtToken);

            // Only consider rotating secure, cookie-based tokens without tool-specific data
            boolean tokenIsConsideredSecure = "cookie".equals(source) && this.tokenProvider.getTools(jwtToken) == null;
            if (tokenIsConsideredSecure && authentication != null) {
                rotateTokenSilently(jwtToken, authentication, httpServletResponse);
            }

            // Set the security context if authentication succeeded
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // Continue with the remaining filters in the chain
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Extracts the valid jwt found in the cookie or the Authorization header
     *
     * @param httpServletRequest the http request
     * @param tokenProvider      the Artemis token provider used to generate and validate jwt's
     * @return the valid jwt or null if not found or invalid
     */
    @Nullable
    public static JwtWithSource extractValidJwt(@NonNull HttpServletRequest httpServletRequest, @NonNull TokenProvider tokenProvider) throws IllegalArgumentException {
        final String requestUri = httpServletRequest.getRequestURI();
        if (isIgnoredUri(requestUri)) {
            return null;
        }

        var cookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        var authHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);

        if (cookie == null && authHeader == null) {
            return null;
        }

        if (cookie != null && authHeader != null) {
            // Single Method Enforcement: Only one method of authentication is allowed
            throw new IllegalArgumentException("Multiple authentication methods detected: Both JWT cookie and Bearer token are present");
        }

        String jwtToken = cookie != null ? getJwtFromCookie(cookie) : getJwtFromBearer(authHeader);
        String source = cookie != null ? "cookie" : "bearer";

        if (!isJwtValid(tokenProvider, jwtToken, source)) {
            // Suppress noisy logs for websocket or cookie-based tokens
            if ("/websocket/websocket".equals(requestUri) && "cookie".equals(source)) {
                return null;
            }
            // Log the invalid JWT token details to find out how it was created in case of accidental issues
            log.debug("Invalid JWT token detected. Details: { source: {}, remote_ip: {}, user_agent: {}, request_uri: {}, headers: {} }", source,
                    httpServletRequest.getRemoteAddr(), httpServletRequest.getHeader(HttpHeaders.USER_AGENT), requestUri, compactHeaders(httpServletRequest));
            return null;
        }

        return new JwtWithSource(jwtToken, source);
    }

    private static String compactHeaders(HttpServletRequest request) {
        if (request.getHeaderNames() == null) {
            return "{}";
        }
        return Collections.list(request.getHeaderNames()).stream().map(name -> name + "=" + request.getHeader(name)).collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Collects the headers of the request and formats them as a JSON-like string
     *
     * @param request the http request
     * @return the formatted headers
     */
    private static String collectHeaders(HttpServletRequest request) {
        List<String> headerEntries = new ArrayList<>();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> headerEntries.add("\"%s\": \"%s\"".formatted(headerName, request.getHeader(headerName))));
        return "[\n" + String.join(",\n", headerEntries) + "\n]";
    }

    /**
     * Extracts the jwt from the cookie
     *
     * @param jwtCookie the cookie with Key "jwt"
     * @return the jwt or null if not found
     */
    @Nullable
    private static String getJwtFromCookie(@Nullable Cookie jwtCookie) {
        if (jwtCookie == null) {
            return null;
        }
        return jwtCookie.getValue();
    }

    /**
     * Extracts the jwt from the Authorization header
     *
     * @param jwtBearer the content of the Authorization header
     * @return the jwt or null if not found
     */
    @Nullable
    private static String getJwtFromBearer(@Nullable String jwtBearer) {
        if (!StringUtils.hasText(jwtBearer) || !jwtBearer.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = jwtBearer.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * Checks if the jwt is valid
     *
     * @param tokenProvider used to generate and validate jwt's
     * @param jwtToken      which should be validated
     * @param source        of the jwt token
     * @return true if the jwt is valid, false if missing or invalid
     */
    private static boolean isJwtValid(@NonNull TokenProvider tokenProvider, @Nullable String jwtToken, @Nullable String source) {
        return StringUtils.hasText(jwtToken) && tokenProvider.validateTokenForAuthority(jwtToken, source);
    }

    /**
     * Not all URIs need JWT authentication.
     *
     * <p>
     * For example, Git clones/pushes use an HTTP token mechanism.
     *
     * @param uri A URI relative to the Artemis base URL, i.e. without the {@code https://artemis.domain.com} part at the start.
     * @return True, if the URI does not use JWT authentication.
     */
    private static boolean isIgnoredUri(final String uri) {
        // /git/**: used by Git clients with a token mechanism
        // /api/iris/internal/** used by Pyris status callbacks with a token mechanism restricted to internal access
        // /api/programming/public/programming-exercises/new-result: used by Jenkins to send test results back to Artemis,
        // uses a separate secret token.
        return uri.startsWith("/git/") || uri.startsWith("/api/iris/internal/") || "/api/programming/public/programming-exercises/new-result".equals(uri);
    }
}
