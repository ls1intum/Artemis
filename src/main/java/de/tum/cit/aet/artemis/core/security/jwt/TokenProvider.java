package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Profile(PROFILE_CORE)
@Component
@Lazy
public class TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private static final String AUTHORITIES_KEY = "auth";

    private static final String AUTHENTICATION_METHOD = "auth-method";

    public static final String IS_AUTHENTICATED_WITH_PASSKEY = "is-authenticated-with-passkey";

    public static final String IS_PASSKEY_SUPER_ADMIN_APPROVED = "is-passkey-super-admin-approved";

    /**
     * Identifies the passkey a token was issued for, so that a later silent rotation can check whether that passkey still
     * exists. Without it a token outlives the credential that created it: deleting a passkey would not stop the sessions
     * it had already produced from being extended.
     */
    public static final String PASSKEY_CREDENTIAL_ID = "passkey-credential-id";

    private static final String TOOLS_KEY = "tools";

    /** Whether the session was established with "remember me", which is what makes it eligible for extension at all. */
    public static final String REMEMBER_ME = "remember-me";

    private SecretKey key;

    private long tokenValidityInMilliseconds;

    private long tokenValidityInMillisecondsForRememberMe;

    private final ArtemisProperties jHipsterProperties;

    private final SecurityMetersService securityMetersService;

    public TokenProvider(ArtemisProperties jHipsterProperties, SecurityMetersService securityMetersService) {
        this.jHipsterProperties = jHipsterProperties;
        this.securityMetersService = securityMetersService;
    }

    /**
     * initializes the token provider based on the yml config file
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes;
        String secret = jHipsterProperties.getSecurity().getAuthentication().getJwt().getSecret();
        if (StringUtils.hasLength(secret)) {
            log.warn("Warning: the JWT key used is not Base64-encoded. We recommend using the `jhipster.security.authentication.jwt.base64-secret` key for optimum security.");
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        else {
            log.debug("Using a Base64-encoded JWT secret key");
            keyBytes = Decoders.BASE64.decode(jHipsterProperties.getSecurity().getAuthentication().getJwt().getBase64Secret());
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.tokenValidityInMilliseconds = 1000 * jHipsterProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
        this.tokenValidityInMillisecondsForRememberMe = 1000 * jHipsterProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSecondsForRememberMe();
    }

    /**
     * Gets the validity for the generated tokens.
     *
     * @param rememberMe Determines Token lifetime
     * @return long The validity of the generated tokens in milliseconds
     */
    public long getTokenValidity(boolean rememberMe) {
        return rememberMe ? this.tokenValidityInMillisecondsForRememberMe : this.tokenValidityInMilliseconds;
    }

    /**
     * Create JWT Token a fully populated <code>Authentication</code> object.
     *
     * @param authentication Authentication Object
     * @param rememberMe     Determines Token lifetime
     * @return JWT Token
     */
    @NonNull
    public String createToken(Authentication authentication, boolean rememberMe) {
        Date now = new Date();
        return createToken(authentication, now, new Date(now.getTime() + getTokenValidity(rememberMe)), null, null, null, false, rememberMe);
    }

    /**
     * Create JWT Token a fully populated <code>Authentication</code> object.
     *
     * @param authentication Authentication Object
     * @param duration       the Token lifetime in milliseconds
     * @param tool           tool this token is used for. If null, it's a general access token
     * @return JWT Token
     */
    @NonNull
    public String createToken(Authentication authentication, long duration, @Nullable ToolTokenType tool) {
        return createToken(authentication, duration, tool, false);
    }

    /**
     * Create JWT Token a fully populated <code>Authentication</code> object.
     *
     * @param authentication Authentication Object
     * @param duration       the Token lifetime in milliseconds
     * @param tool           tool this token is used for. If null, it's a general access token
     * @param rememberMe     whether the session was established with "remember me", which is what makes it extendable
     * @return JWT Token
     */
    @NonNull
    public String createToken(Authentication authentication, long duration, @Nullable ToolTokenType tool, boolean rememberMe) {
        long validity = System.currentTimeMillis() + duration;
        boolean isPasskeyApproved = false;
        String passkeyCredentialId = null;
        if (authentication.getDetails() instanceof Map<?, ?> details) {
            isPasskeyApproved = Boolean.TRUE.equals(details.get(IS_PASSKEY_SUPER_ADMIN_APPROVED));
            if (details.get(PASSKEY_CREDENTIAL_ID) instanceof String credentialId) {
                passkeyCredentialId = credentialId;
            }
        }
        return createToken(authentication, null, new Date(validity), tool, null, passkeyCredentialId, isPasskeyApproved, rememberMe);
    }

    /**
     * Create JWT Token a fully populated {@link Authentication} object.
     *
     * @param authentication           Authentication Object
     * @param issuedAt                 Date when the token was issued, if null set to now
     * @param expiration               Date when the token expires
     * @param tool                     tool this token is used for. If null, it's a general access token
     * @param authenticatedWithPasskey can be manually set to true if the token was created with a passkey but for performance reasons, no actual WebAuthnAuthentication was created
     * @return JWT Token
     */
    @NonNull
    public String createToken(Authentication authentication, @Nullable Date issuedAt, Date expiration, @Nullable ToolTokenType tool, @Nullable Boolean authenticatedWithPasskey) {
        boolean isPasskeyApproved = false;
        String passkeyCredentialId = null;
        if (authentication.getDetails() instanceof Map<?, ?> details) {
            isPasskeyApproved = Boolean.TRUE.equals(details.get(IS_PASSKEY_SUPER_ADMIN_APPROVED));
            if (details.get(PASSKEY_CREDENTIAL_ID) instanceof String credentialId) {
                passkeyCredentialId = credentialId;
            }
        }
        return createToken(authentication, issuedAt, expiration, tool, authenticatedWithPasskey, passkeyCredentialId, isPasskeyApproved);
    }

    /**
     * Creates a token with the passkey claims supplied explicitly rather than read from the authentication details.
     * <p>
     * Silent rotation needs this: the {@link Authentication} it works with was rebuilt from the expiring token and carries
     * no details, so deriving the passkey claims from it would drop them. Losing the credential id would defeat the check
     * that the passkey still exists, and losing the approval flag would silently downgrade a super admin.
     *
     * @param authentication              the authentication to create the token for
     * @param issuedAt                    when the token was originally issued, preserved across rotations
     * @param expiration                  when the token expires
     * @param tool                        the tool the token is scoped to, if any
     * @param authenticatedWithPasskey    whether the session was established with a passkey
     * @param passkeyCredentialId         the passkey the session belongs to, or {@code null} if it is not a passkey session
     * @param isPasskeySuperAdminApproved whether the passkey is approved for super-admin access
     * @return the signed token
     */
    public String createToken(Authentication authentication, @Nullable Date issuedAt, Date expiration, @Nullable ToolTokenType tool, @Nullable Boolean authenticatedWithPasskey,
            @Nullable String passkeyCredentialId, boolean isPasskeySuperAdminApproved) {
        return createToken(authentication, issuedAt, expiration, tool, authenticatedWithPasskey, passkeyCredentialId, isPasskeySuperAdminApproved, false);
    }

    /**
     * Creates a token that also records whether the session is a "remember me" session, which is what makes it eligible
     * for silent extension at all. How long such a session may live is bounded by the absolute ceiling applied while
     * rotating, measured from {@code issuedAt}, rather than by counting extensions.
     *
     * @param authentication              the authentication to create the token for
     * @param issuedAt                    when the session was originally established, preserved across rotations
     * @param expiration                  when this token expires
     * @param tool                        the tool the token is scoped to, if any
     * @param authenticatedWithPasskey    whether the session was established with a passkey
     * @param passkeyCredentialId         the passkey the session belongs to, or {@code null}
     * @param isPasskeySuperAdminApproved whether the passkey is approved for super-admin access
     * @param rememberMe                  whether the session was established with "remember me"
     * @return the signed token
     */
    public String createToken(Authentication authentication, @Nullable Date issuedAt, Date expiration, @Nullable ToolTokenType tool, @Nullable Boolean authenticatedWithPasskey,
            @Nullable String passkeyCredentialId, boolean isPasskeySuperAdminApproved, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));

        AuthenticationMethod authenticationMethod = AuthenticationMethod.fromAuthentication(authentication);
        if (authenticatedWithPasskey != null && authenticatedWithPasskey) {
            authenticationMethod = AuthenticationMethod.PASSKEY;
        }

        boolean isPasskeyApproved = authenticationMethod == AuthenticationMethod.PASSKEY && isPasskeySuperAdminApproved;

        // @formatter:off
        JwtBuilder jwtBuilder = Jwts.builder()
            .subject(authentication.getName())
            .claim(AUTHORITIES_KEY, authorities)
            .claim(AUTHENTICATION_METHOD, authenticationMethod)
            .claim(IS_PASSKEY_SUPER_ADMIN_APPROVED, isPasskeyApproved)
            .issuedAt(issuedAt != null ? issuedAt : new Date());
        // @formatter:on

        if (passkeyCredentialId != null) {
            jwtBuilder.claim(PASSKEY_CREDENTIAL_ID, passkeyCredentialId);
        }

        if (rememberMe) {
            jwtBuilder.claim(REMEMBER_ME, true);
        }

        if (tool != null) {
            jwtBuilder.claim(TOOLS_KEY, tool);
        }

        return jwtBuilder.signWith(key, Jwts.SIG.HS512).expiration(expiration).compact();
    }

    /**
     * Convert JWT Authorization Token into UsernamePasswordAuthenticationToken, including a USer object and its authorities
     *
     * @param token JWT Authorization Token
     * @return UsernamePasswordAuthenticationToken with principal, token and authorities that were stored in the token or null if no authorities were found
     */
    @Nullable
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        var authorityClaim = claims.get(AUTHORITIES_KEY);
        if (authorityClaim == null) {
            // leads to a 401 unauthorized error
            return null;
        }
        List<? extends GrantedAuthority> authorities = Arrays.stream(authorityClaim.toString().split(",")).map(SimpleGrantedAuthority::new).toList();

        User principal = new User(claims.getSubject(), "", authorities);
        var authentication = new UsernamePasswordAuthenticationToken(principal, token, authorities);

        // Keep the already verified authentication claims with the request authentication. Consumers such as JWTFilter
        // can derive request-scoped capabilities without parsing and verifying the signed token again.
        Map<String, Object> details = new HashMap<>();
        details.put(IS_AUTHENTICATED_WITH_PASSKEY, getAuthenticationMethod(claims) == AuthenticationMethod.PASSKEY);
        details.put(IS_PASSKEY_SUPER_ADMIN_APPROVED, isPasskeySuperAdminApproved(claims));
        String passkeyCredentialId = claims.get(PASSKEY_CREDENTIAL_ID, String.class);
        if (passkeyCredentialId != null) {
            details.put(PASSKEY_CREDENTIAL_ID, passkeyCredentialId);
        }
        authentication.setDetails(Map.copyOf(details));
        return authentication;
    }

    /**
     * Validate an JWT Authorization Token
     *
     * @param authToken JWT Authorization Token
     * @param source    the source of the token
     * @return boolean indicating if token is valid
     */
    public boolean validateTokenForAuthority(String authToken, @Nullable String source) {
        return validateJwsToken(authToken, source);
    }

    /**
     * Validate an JWT Authorization Token
     *
     * @param authToken JWT Authorization Token
     * @param source    the source of the token
     * @return boolean indicating if token is valid
     */
    private boolean validateJwsToken(String authToken, @Nullable String source) {
        try {
            parseClaims(authToken);
            return true;
        }
        catch (ExpiredJwtException e) {
            this.securityMetersService.trackTokenExpired();
            log.trace("Invalid (expired) JWT token.", e);
        }
        catch (UnsupportedJwtException e) {
            this.securityMetersService.trackTokenUnsupported();
            log.trace("Invalid (unsupported) JWT token.", e);
        }
        catch (MalformedJwtException e) {
            this.securityMetersService.trackTokenMalformed();
            log.trace("Invalid (malformed) JWT token.", e);
        }
        catch (SignatureException e) {
            this.securityMetersService.trackTokenInvalidSignature();
            log.trace("Invalid (signature) JWT token.", e);
        }
        catch (IllegalArgumentException e) {
            log.error("Token validation error {}", e.getMessage());
        }
        log.debug("Invalid JWT token: {} from source {}", authToken, source);
        return false;
    }

    /**
     * Verifies the signature and returns the claims.
     * <p>
     * Every accessor below goes through this, so each one is a full signature verification. A caller that needs more than
     * one claim of the same token should parse once and use the {@link Claims} overloads instead of calling several of the
     * {@code String} accessors - {@link JWTFilter} does that on the request path, where the accessors would otherwise
     * verify the same token several times per request.
     *
     * @param authToken the token to read
     * @return the verified claims
     */
    @NonNull
    Claims parseClaims(String authToken) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken).getPayload();
    }

    /**
     * @param authToken the token to read
     * @return whether the session was established with "remember me"
     */
    public boolean isRememberMeSession(String authToken) {
        return isRememberMeSession(parseClaims(authToken));
    }

    /**
     * @param claims claims of an already parsed token
     * @return whether the session was established with "remember me"
     */
    boolean isRememberMeSession(Claims claims) {
        return Boolean.TRUE.equals(claims.get(REMEMBER_ME, Boolean.class));
    }

    /**
     * @param authToken the token to read
     * @return the passkey this token was issued for, or {@code null} for tokens that are not passkey tokens and for
     *         passkey tokens issued before the claim was introduced
     */
    @Nullable
    public String getPasskeyCredentialId(String authToken) {
        return getPasskeyCredentialId(parseClaims(authToken));
    }

    /**
     * @param claims claims of an already parsed token
     * @return the passkey this token was issued for, or {@code null} if it carries no credential id
     */
    @Nullable
    String getPasskeyCredentialId(Claims claims) {
        return claims.get(PASSKEY_CREDENTIAL_ID, String.class);
    }

    @NonNull
    public <T> T getClaim(String token, String claimName, Class<T> claimType) {
        Claims claims = parseClaims(token);
        return claims.get(claimName, claimType);
    }

    @NonNull
    public Date getExpirationDate(String authToken) {
        return parseClaims(authToken).getExpiration();
    }

    @NonNull
    public Date getIssuedAtDate(String authToken) {
        return parseClaims(authToken).getIssuedAt();
    }

    /**
     * @param authToken of which the tools should be extracted
     * @return {@link ToolTokenType} if the token contains a tool, null otherwise
     */
    @Nullable
    public ToolTokenType getTools(String authToken) {
        return getTools(parseClaims(authToken));
    }

    /**
     * @param claims claims of an already parsed token
     * @return {@link ToolTokenType} if the token contains a tool, null otherwise
     */
    @Nullable
    ToolTokenType getTools(Claims claims) {
        String toolString = claims.get(TOOLS_KEY, String.class);

        if (toolString == null) {
            return null;
        }

        return ToolTokenType.valueOf(toolString);
    }

    /**
     * @param authToken of which the authentication type on login should be extracted
     * @return {@link AuthenticationMethod} that was used to create the token
     */
    @Nullable
    public AuthenticationMethod getAuthenticationMethod(String authToken) {
        try {
            return getAuthenticationMethod(parseClaims(authToken));
        }
        catch (UnsupportedJwtException | IllegalArgumentException e) {
            log.warn("Failed to parse authentication method from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @param claims claims of an already parsed token
     * @return {@link AuthenticationMethod} that was used to create the token, or null if it carries none or one this
     *         version does not recognise
     */
    @Nullable
    AuthenticationMethod getAuthenticationMethod(Claims claims) {
        try {
            String method = claims.get(AUTHENTICATION_METHOD, String.class);
            return method != null ? AuthenticationMethod.fromMethod(method) : null;
        }
        catch (IllegalArgumentException e) {
            // Same tolerance as the String accessor: an unrecognised or wrongly typed claim must not fail the request it
            // arrived on, and JWTFilter reads this on every authenticated request.
            log.warn("Failed to parse authentication method from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @param authToken of which the passkey super admin approval status should be extracted
     * @return true if the passkey was super admin approved, false otherwise
     */
    public boolean isPasskeySuperAdminApproved(String authToken) {
        try {
            return isPasskeySuperAdminApproved(parseClaims(authToken));
        }
        catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            log.warn("Failed to parse passkey super admin approval status from token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * @param claims claims of an already parsed token
     * @return true if the passkey was super admin approved, false otherwise
     */
    boolean isPasskeySuperAdminApproved(Claims claims) {
        try {
            return Boolean.TRUE.equals(claims.get(IS_PASSKEY_SUPER_ADMIN_APPROVED, Boolean.class));
        }
        catch (IllegalArgumentException e) {
            log.warn("Failed to parse passkey super admin approval status from token: {}", e.getMessage());
            return false;
        }
    }
}
