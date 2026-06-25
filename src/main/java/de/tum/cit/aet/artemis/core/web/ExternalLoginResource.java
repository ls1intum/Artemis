package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.ExternalLoginProperties;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginCodeRequestDTO;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginCodeResponseDTO;
import de.tum.cit.aet.artemis.core.repository.externallogin.HazelcastExternalLoginCodeRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.externallogin.ExternalLoginCodeData;
import de.tum.cit.aet.artemis.core.security.externallogin.ExternalLoginRedirectUriValidator;
import de.tum.cit.aet.artemis.core.security.externallogin.PkceUtil;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Authenticated endpoint that issues a one-time code for the external-client browser login handoff
 * (e.g. the VS Code extension).
 * <p>
 * Called same-origin from the Artemis web app's external-login page after the user has logged in by
 * any method (passkey, SAML, password). The full JWT is minted here, capped to the current session's
 * remaining validity (so this flow can never extend a session), bound to the one-time code, and only
 * released after the external client proves possession of the PKCE verifier (see
 * {@link de.tum.cit.aet.artemis.core.web.open.PublicExternalLoginResource}).
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/external-login/")
public class ExternalLoginResource {

    private static final Logger log = LoggerFactory.getLogger(ExternalLoginResource.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int CODE_BYTES = 32; // 256 bits of entropy

    private static final int MAX_LOGGED_REJECTION_REASON_LENGTH = 200;

    private final ExternalLoginProperties externalLoginProperties;

    private final HazelcastExternalLoginCodeRepository codeRepository;

    private final TokenProvider tokenProvider;

    public ExternalLoginResource(ExternalLoginProperties externalLoginProperties, HazelcastExternalLoginCodeRepository codeRepository, TokenProvider tokenProvider) {
        this.externalLoginProperties = externalLoginProperties;
        this.codeRepository = codeRepository;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Builds the callback validator from the current configuration. Constructed per request (cheap) so that the
     * feature flag and the allowlists reflect the live configuration instead of being frozen at construction time.
     *
     * @return the redirect URI validator for the current configuration
     */
    private ExternalLoginRedirectUriValidator validator() {
        return new ExternalLoginRedirectUriValidator(externalLoginProperties.getAllowedRedirectSchemes(), externalLoginProperties.getAllowedRedirectAuthorities());
    }

    /**
     * Issues a one-time code that an external client can exchange (with its PKCE verifier) for a full JWT.
     *
     * @param body    the PKCE S256 code challenge and the extension callback URI
     * @param request the HTTP request, used to read the current session's JWT for the lifetime cap
     * @return 200 with the one-time code; 400 on invalid input; 404 if the feature is disabled; 401 if the session token is missing
     */
    @PostMapping("code")
    @EnforceAtLeastStudent
    public ResponseEntity<ExternalLoginCodeResponseDTO> issueCode(@RequestBody ExternalLoginCodeRequestDTO body, HttpServletRequest request) {
        ExternalLoginRedirectUriValidator validator = validator();
        if (!validator.isFeatureEnabled()) {
            return ResponseEntity.notFound().build();
        }
        // Reject malformed PKCE challenges up front: a non-S256-shaped challenge could never be matched at exchange,
        // so we fail fast with 400 instead of minting a dead-on-arrival, JWT-bearing code.
        if (body == null || !PkceUtil.isValidS256Challenge(body.codeChallenge())) {
            return ResponseEntity.badRequest().build();
        }
        Optional<String> rejection = validator.validate(body.callback());
        if (rejection.isPresent()) {
            // The rejection reason can embed attacker-controlled callback input, so sanitize it before logging.
            log.warn("External-login callback rejected: {}", sanitizeForLog(rejection.get()));
            return ResponseEntity.badRequest().build();
        }

        // Cap the minted token to the current session's remaining validity, so this flow can never extend a session.
        JwtWithSource current = JWTFilter.extractValidJwt(request, tokenProvider);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Date sessionExpiry = tokenProvider.getExpirationDate(current.jwt());
        if (sessionExpiry.getTime() - System.currentTimeMillis() <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Authentication authentication = tokenProvider.getAuthentication(current.jwt());
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Mint a full (unscoped) JWT for the current principal, stored server-side until the code is exchanged.
        // Preserve the originating session's authentication method and passkey-approval claims so a passkey/SAML
        // browser login does not silently degrade to a password token (which would break passkey-approved admins).
        AuthenticationMethod authenticationMethod = tokenProvider.getAuthenticationMethod(current.jwt());
        boolean isPasskeyApproved = tokenProvider.isPasskeySuperAdminApproved(current.jwt());
        String jwt = tokenProvider.createToken(authentication, null, sessionExpiry, null, authenticationMethod, isPasskeyApproved);
        String code = generateCode();
        codeRepository.save(code, new ExternalLoginCodeData(jwt, body.codeChallenge(), body.callback()));

        return ResponseEntity.ok(new ExternalLoginCodeResponseDTO(code));
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Strips line breaks and bounds the length of a callback-rejection reason before it is logged, so attacker-controlled
     * callback input cannot inject forged log lines (log poisoning) or bloat the logs.
     *
     * @param reason the rejection reason from the validator
     * @return a single-line, length-bounded version safe to log
     */
    private static String sanitizeForLog(String reason) {
        String singleLine = reason.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() > MAX_LOGGED_REJECTION_REASON_LENGTH ? singleLine.substring(0, MAX_LOGGED_REJECTION_REASON_LENGTH) : singleLine;
    }
}
