package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.ExternalLoginTokenRequestDTO;
import de.tum.cit.aet.artemis.core.dto.ExternalLoginTokenResponseDTO;
import de.tum.cit.aet.artemis.core.repository.externallogin.HazelcastExternalLoginCodeRepository;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.security.externallogin.ExternalLoginCodeData;
import de.tum.cit.aet.artemis.core.security.externallogin.PkceUtil;

/**
 * Public endpoint that exchanges a one-time code + PKCE verifier for a full JWT.
 * <p>
 * Public because the external client is not yet authenticated; possession of the PKCE verifier (which
 * never reaches the browser) authorizes the exchange. The code is single-use and short-lived, and the
 * endpoint is rate-limited.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/public/external-login/")
public class PublicExternalLoginResource {

    private final HazelcastExternalLoginCodeRepository codeRepository;

    public PublicExternalLoginResource(HazelcastExternalLoginCodeRepository codeRepository) {
        this.codeRepository = codeRepository;
    }

    /**
     * Exchanges a one-time code and PKCE verifier for the full JWT minted at issue time.
     *
     * @param body the one-time code and the PKCE code verifier
     * @return 200 with the access token on success; 401 if the code is unknown/expired/consumed or the verifier is wrong
     */
    @PostMapping("token")
    @EnforceNothing
    @LimitRequestsPerMinute(type = RateLimitType.AUTHENTICATION)
    public ResponseEntity<ExternalLoginTokenResponseDTO> exchangeCode(@RequestBody ExternalLoginTokenRequestDTO body) {
        // Reject blank codes and malformed/overlong PKCE verifiers before any repository or hashing work.
        if (body == null || body.code() == null || body.code().isBlank() || !PkceUtil.isValidCodeVerifier(body.codeVerifier())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Single-use: atomically consume the code, then verify the PKCE verifier against the stored challenge.
        ExternalLoginCodeData data = codeRepository.consume(body.code());
        if (data == null || !PkceUtil.matches(body.codeVerifier(), data.codeChallenge())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new ExternalLoginTokenResponseDTO(data.jwt()));
    }
}
