package de.tum.in.www1.artemis.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.management.SecurityMetersService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import tech.jhipster.config.JHipsterProperties;

@Component
public class TokenProvider {

    private final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private static final String AUTHORITIES_KEY = "auth";

    private static final String FILENAME_KEY = "filename";

    private static final String COURSE_ID_KEY = "courseId";

    public static final String DOWNLOAD_FILE = "FILE_DOWNLOAD";

    private Key key;

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
     * Create JWT Token a fully populated <code>Authentication</code> object.
     * @param authentication Authentication Object
     * @param rememberMe Determines Token lifetime (30 minutes vs 30 days)
     * @return JWT Token
     */
    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity;
        if (rememberMe) {
            validity = new Date(now + this.tokenValidityInMillisecondsForRememberMe);
        }
        else {
            validity = new Date(now + this.tokenValidityInMilliseconds);
        }

        return Jwts.builder().setSubject(authentication.getName()).claim(AUTHORITIES_KEY, authorities).signWith(key, SignatureAlgorithm.HS512).setExpiration(validity).compact();
    }

    /**
     * Generates an access token that allows a user to download a file. This token is only valid for the given validity period.
     *
     * @param authentication Currently active authentication mostly the currently logged in user
     * @param durationValidityInSeconds The duration how long the access token should be valid
     * @param fileName The name of the file, which the token belongs to
     * @return File access token as a JWT token
     */
    public String createFileTokenWithCustomDuration(Authentication authentication, Integer durationValidityInSeconds, String fileName) throws IllegalAccessException {
        // Note: we want to prevent issues with authentication here. Filenames should never contain commas!
        if (fileName.contains(",")) {
            throw new IllegalAccessException("File names cannot include commas");
        }
        String fileNameValue = DOWNLOAD_FILE + fileName;

        long now = (new Date()).getTime();
        Date validity = new Date(now + durationValidityInSeconds * 1000);

        return Jwts.builder().setSubject(authentication.getName()).claim(FILENAME_KEY, fileNameValue).signWith(key, SignatureAlgorithm.HS512).setExpiration(validity).compact();
    }

    /**
     * Generates an access token that allows a user to download a file of a course. This token is only valid for the given validity period.
     *
     * @param authentication Currently active authentication mostly the currently logged in user
     * @param durationValidityInSeconds The duration how long the access token should be valid
     * @param courseId The id of the course, which the token belongs to
     * @return File access token as a JWT token
     */
    public String createFileTokenForCourseWithCustomDuration(Authentication authentication, Integer durationValidityInSeconds, Long courseId) throws IllegalAccessException {
        long now = (new Date()).getTime();
        Date validity = new Date(now + durationValidityInSeconds * 1000);

        return Jwts.builder().setSubject(authentication.getName()).claim(COURSE_ID_KEY, courseId).signWith(key, SignatureAlgorithm.HS512).setExpiration(validity).compact();
    }

    /**
     * Convert JWT Authorization Token into UsernamePasswordAuthenticationToken, including a USer object and its authorities
     * @param token JWT Authorization Token
     * @return UsernamePasswordAuthenticationToken
     */
    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        var authorityClaim = claims.get(AUTHORITIES_KEY);
        if (authorityClaim == null) {
            // leads to a 401 unauthorized error
            return null;
        }
        Collection<? extends GrantedAuthority> authorities = Arrays.stream(authorityClaim.toString().split(",")).map(SimpleGrantedAuthority::new).collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    /**
     * Checks if a certain downloadFileKey and filename is inside the JWT token. Also validates the JWT token if it is still valid.
     *
     * @param authToken The token that has to be checked
     * @param downloadFileKey What downloadFileKey should be included in the token
     * @param fileName The name of the file the token belongs to
     * @return true if everything matches
     */
    public boolean validateTokenForAuthorityAndFile(String authToken, String downloadFileKey, String fileName) {
        if (!validateJwsToken(authToken)) {
            return false;
        }
        try {
            String fileNameToken = (String) Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken).getBody().get(FILENAME_KEY);
            return fileNameToken.contains(downloadFileKey + fileName);
        }
        catch (Exception e) {
            log.warn("Invalid action validateTokenForAuthorityAndFile: ", e);
        }
        return false;
    }

    /**
     * Checks if a certain course id is inside the JWT token. Also validates the JWT token if it is still valid.
     *
     * @param authToken The token that has to be checked
     * @param courseId  The course id the token belongs to
     * @return true if everything matches
     */
    public boolean validateTokenForAuthorityAndCourse(String authToken, Long courseId) {
        if (!validateJwsToken(authToken)) {
            return false;
        }
        try {
            Long courseIdToken = ((Integer) Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken).getBody().get(COURSE_ID_KEY)).longValue();
            return courseIdToken.equals(courseId);
        }
        catch (Exception e) {
            log.warn("Invalid action validateTokenForAuthorityAndCourse: ", e);
        }
        return false;
    }

    /**
     * Validate an JWT Authorization Token
     * @param authToken JWT Authorization Token
     * @return boolean indicating if token is valid
     */
    public boolean validateTokenForAuthority(String authToken) {
        return validateJwsToken(authToken);
    }

    /**
     * Validate an JWT Authorization Token
     * @param authToken JWT Authorization Token
     * @return boolean indicating if token is valid
     */
    private boolean validateJwsToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
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
        log.info("Invalid JWT token: {}", authToken);
        return false;
    }
}
