package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URISyntaxException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasskeyDto;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.repository.webauthn.ArtemisUserCredentialRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

/**
 * REST controller for public endpoints regarding the webauthn (Web Authentication) API, e.g. used for passkeys.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/webauthn/")
public class WebauthnResource {

    public static final String ENTITY_NAME = "webauthn";

    private static final Logger log = LoggerFactory.getLogger(WebauthnResource.class);

    private final UserRepository userRepository;

    private final ArtemisUserCredentialRepository artemisUserCredentialRepository;

    public WebauthnResource(UserRepository userRepository, ArtemisUserCredentialRepository artemisUserCredentialRepository) {
        this.userRepository = userRepository;
        this.artemisUserCredentialRepository = artemisUserCredentialRepository;
    }

    @GetMapping("users/{userId}/passkeys")
    @EnforceNothing
    public ResponseEntity<List<PasskeyDto>> getPasskeys(@PathVariable Long userId) {
        log.info("Retrieving passkeys for user with id: {}", userId);

        var user = userRepository.getUser();
        List<PasskeyDto> passkeys = artemisUserCredentialRepository.findPasskeyDtosByUserId(User.longToBytes(user.getId()));

        return ResponseEntity.ok(passkeys);
    }

    /**
     * {@code Get /authenticate} : authenticate as a user with a passkey.
     */
    // @PostMapping("authenticate")
    // @EnforceNothing
    // public ResponseEntity<Void> authenticate(HttpServletRequest request, @Valid @RequestBody AuthenticateDTO authenticateDTO, BindingResult result) throws URISyntaxException {
    // TODO endpoint does not work yet
    @PostMapping("authenticate")
    @EnforceNothing
    public ResponseEntity<Void> authenticate(HttpServletRequest request, HttpServletResponse response, JWTCookieService jwtCookieService,
            AuthenticationManager authenticationManager) throws URISyntaxException {
        // try {
        // WebAuthnAuthenticationFilter filter = new WebAuthnAuthenticationFilter();
        // filter.setAuthenticationManager(authenticationManager);
        // filter.attemptAuthentication(request, response);
        // webAuthnAuthenticationFilter.setAuthenticationManager(authenticationManager);
        // webAuthnAuthenticationFilter.attemptAuthentication(request, response);
        // }
        // catch (IOException | ServletException e) {
        // log.error("Error during WebAuthn authentication", e);
        // return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).build();
        // }

        // RelyingPartyAuthenticationRequest authenticationRequest = new RelyingPartyAuthenticationRequest(
        // webAuthnAuthenticateDto.rawId().getBytes(),
        // webAuthnAuthenticateDto.response().clientDataJSON().getBytes(),
        // webAuthnAuthenticateDto.response().authenticatorData().getBytes(),
        // webAuthnAuthenticateDto.response().signature().getBytes(),
        // webAuthnAuthenticateDto.clientExtensionResults().toString()
        // );
        //
        // WebAuthnAuthenticationRequestToken authenticationToken = new WebAuthnAuthenticationRequestToken(authenticationRequest);
        //
        // try {
        // Authentication authentication = authenticationManager.authenticate(authenticationToken);
        // SecurityContextHolder.getContext().setAuthentication(authentication);

        // boolean rememberMe = true; // TODO adjust properly
        //
        // ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
        // response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        // return ResponseEntity.ok().build();
        // } catch (BadCredentialsException exception) {
        // // TODO unauthoroized vs bad credentials
        // return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        // }
        return ResponseEntity.ok().build();
    }
}
