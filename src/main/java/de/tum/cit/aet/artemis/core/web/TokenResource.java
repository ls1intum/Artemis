package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class TokenResource {

    private final JWTCookieService jwtCookieService;

    private final TokenProvider tokenProvider;

    public TokenResource(JWTCookieService jwtCookieService, TokenProvider tokenProvider) {
        this.jwtCookieService = jwtCookieService;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Sends a tool token back as cookie or bearer token
     *
     * @param tool     the tool for which the token is requested
     * @param asCookie if true the token is sent back as a cookie
     * @param request  HTTP request
     * @param response HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) and depending on the asCookie flag a bearer token in the body
     */
    @PostMapping("tool-token")
    @EnforceAtLeastStudent
    public ResponseEntity<String> convertCookieToToolToken(@RequestParam(name = "tool") ToolTokenType tool,
            @RequestParam(name = "as-cookie", defaultValue = "false") boolean asCookie, HttpServletRequest request, HttpServletResponse response) {
        JwtWithSource jwtWithSource = JWTFilter.extractValidJwt(request, tokenProvider);

        if (jwtWithSource == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // get validity of the token
        long tokenRemainingTime = tokenProvider.getExpirationDate(jwtWithSource.jwt()).getTime() - System.currentTimeMillis();

        // 1 day validity
        long maxDuration = Duration.ofDays(1).toMillis();
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(Math.min(tokenRemainingTime, maxDuration), tool);

        if (asCookie) {
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        }
        return ResponseEntity.ok(responseCookie.getValue());
    }
}
