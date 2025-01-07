package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import tech.jhipster.config.JHipsterProperties;

@Profile(PROFILE_CORE)
@Component
public class TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private static final String AUTHORITIES_KEY = "auth";

    private SecretKey key;

    private long tokenValidityInMilliseconds;

    private long tokenValidityInMillisecondsForRememberMe;

    private final JHipsterProperties jHipsterProperties;

    private final SecurityMetersService securityMetersService;

    public TokenProvider(JHipsterProperties jHipsterProperties, SecurityMetersService securityMetersService) {
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
     * @return long The validity of the generated tokens
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
    public String createToken(Authentication authentication, boolean rememberMe) {
        return createToken(authentication, getTokenValidity(rememberMe), null);
    }

    /**
     * Create JWT Token a fully populated <code>Authentication</code> object.
     *
     * @param authentication Authentication Object
     * @param duration       the Token lifetime in milli seconds
     * @param tool           tool this token is used for. If null, it's a general access token
     * @return JWT Token
     */
    public String createToken(Authentication authentication, long duration, @Nullable ToolTokenType tool) {
        String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));

        var validity = System.currentTimeMillis() + duration;
        var jwtBuilder = Jwts.builder().subject(authentication.getName()).claim(AUTHORITIES_KEY, authorities);
        if (tool != null) {
            jwtBuilder.claim("tools", tool);
        }

        return jwtBuilder.signWith(key, Jwts.SIG.HS512).expiration(new Date(validity)).compact();
    }

    /**
     * Convert JWT Authorization Token into UsernamePasswordAuthenticationToken, including a USer object and its authorities
     *
     * @param token JWT Authorization Token
     * @return UsernamePasswordAuthenticationToken with principal, token and authorities that were stored in the token or null if no authorities were found
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        var authorityClaim = claims.get(AUTHORITIES_KEY);
        if (authorityClaim == null) {
            // leads to a 401 unauthorized error
            return null;
        }
        List<? extends GrantedAuthority> authorities = Arrays.stream(authorityClaim.toString().split(",")).map(SimpleGrantedAuthority::new).toList();

        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
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
        log.info("Invalid JWT token: {} from source {}", authToken, source);
        return false;
    }

    private Claims parseClaims(String authToken) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken).getPayload();
    }

    public Date getExpirationDate(String authToken) {
        return parseClaims(authToken).getExpiration();
    }
}
