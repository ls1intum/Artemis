package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URISyntaxException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.springframework.security.WebAuthnAuthenticationRequest;
import com.webauthn4j.springframework.security.WebAuthnAuthenticationToken;
import com.webauthn4j.springframework.security.WebAuthnRegistrationRequestValidationResponse;
import com.webauthn4j.springframework.security.WebAuthnRegistrationRequestValidator;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecord;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordImpl;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordManager;
import com.webauthn4j.springframework.security.exception.WebAuthnAuthenticationException;
import com.webauthn4j.util.exception.WebAuthnException;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.AuthenticateDTO;
import de.tum.cit.aet.artemis.core.dto.CreatePasskeyDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

/**
 * REST controller for public endpoints regarding the webauthn (Web Authentication) API, e.g. used for passkeys.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/public/webauthn/")
public class PublicWebauthnResource {

    public static final String ENTITY_NAME = "webauthn";

    private static final Logger log = LoggerFactory.getLogger(PublicWebauthnResource.class);

    private final WebAuthnRegistrationRequestValidator registrationRequestValidator;

    private final WebAuthnCredentialRecordManager webAuthnAuthenticatorManager;

    private final UserRepository userRepository;

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    private final AuthenticationManager authenticationManager;

    public PublicWebauthnResource(WebAuthnRegistrationRequestValidator registrationRequestValidator, WebAuthnCredentialRecordManager webAuthnAuthenticatorManager,
            UserRepository userRepository, AuthenticationManager authenticationManager) {
        this.registrationRequestValidator = registrationRequestValidator;
        this.webAuthnAuthenticatorManager = webAuthnAuthenticatorManager;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
    }

    /**
     * {@code POST /signup} : register a new passkey for a user.
     *
     * @return ResponseEntity with status 201 (Created) if the passkey was successfully registered.
     */
    @PostMapping("signup")
    @EnforceNothing
    public ResponseEntity<Void> registerNewPasskey(HttpServletRequest request, @Valid @RequestBody CreatePasskeyDTO createPasskeyDTO, BindingResult result) {
        if (result.hasErrors()) {
            log.error("Validation errors: {}", result.getAllErrors());
            throw new BadRequestAlertException("User input validation failed. Please try again.", ENTITY_NAME, "TODO");
        }

        // TODO verify https://chromium.googlesource.com/chromium/src/+/master/content/browser/webauth/client_data_json.md is checked

        ObjectMapper objectMapper = new ObjectMapper();

        String clientExtensionResultsJson;
        try {
            // TODO do this in the class directly
            clientExtensionResultsJson = objectMapper.writeValueAsString(createPasskeyDTO.webAuthnCredential().clientExtensionResults());
            log.debug(clientExtensionResultsJson);
        }
        catch (JsonProcessingException e) {
            log.error("Error converting clientExtensionResults to JSON", e);
            throw new BadRequestAlertException("clientExtensionResults could not be converted to JSON. Please try again.", ENTITY_NAME, "TODO");
        }

        WebAuthnRegistrationRequestValidationResponse registrationRequestValidationResponse;
        try {
            // @formatter:off
            registrationRequestValidationResponse = registrationRequestValidator.validate(
                request,
                createPasskeyDTO.webAuthnCredential().response().clientDataJSON(),
                createPasskeyDTO.webAuthnCredential().response().attestationObject(),
                createPasskeyDTO.webAuthnCredential().response().transports(),
                clientExtensionResultsJson
            );
            // @formatter:on
        }
        catch (WebAuthnException | WebAuthnAuthenticationException exception) {
            throw new BadRequestAlertException("WebAuthn registration request validation failed. Please try again.", ENTITY_NAME, "TODO");
        }
        // valid registrationRequestValidationResponse, we could still reject (e.g. only allow credentials via USB)

        WebAuthnCredentialRecord authenticator = getWebAuthnCredentialRecord(registrationRequestValidationResponse);

        try {
            webAuthnAuthenticatorManager.createCredentialRecord(authenticator);
        }
        catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException("Passkey registration failed", ENTITY_NAME, null);
        }

        // TODO return a proper URI and status 201 instead of 200
        // return ResponseEntity.created().build();
        return ResponseEntity.ok().build();
    }

    /**
     * {@code Get /authenticate} : authenticate as a user with a passkey.
     *
     */
    // @PostMapping("authenticate")
    // @EnforceNothing
    // public ResponseEntity<Void> authenticate(HttpServletRequest request, @Valid @RequestBody AuthenticateDTO authenticateDTO, BindingResult result) throws URISyntaxException {
    // TODO endpoint does not work yet
    @PostMapping("authenticate")
    @EnforceNothing
    public ResponseEntity<Void> authenticate(JWTCookieService jwtCookieService, HttpServletResponse response) throws URISyntaxException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            // throw new BadRequestAlertException("Authentication with passkey was not successful, not authenticated", ENTITY_NAME, null);
            // TODO Unauthorzied Exception werfen (oder badCredentials wenn es das nicht gibt)
            // HttpClientErrorException.unauthorzied
            // UnauthorizedException
            // BadCredentials
        }

        //
        // WebAuthnAuthenticationToken authenticationToken = getWebAuthnAuthenticationToken(authenticateDTO);
        //
        // Authentication authResult = authenticationManager.authenticate(authenticationToken);
        // SecurityContextHolder.getContext().setAuthentication(authResult);
        // log.info("User is authenticated");

        // boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();
        boolean rememberMe = true;

        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        // return ResponseEntity.ok(Map.of("access_token", responseCookie.getValue()));

        // TODO hier response cookie zurückgeben, Https ServletR
        // TODO remember me setzen
        return ResponseEntity.ok().build();
    }

    private static WebAuthnAuthenticationToken getWebAuthnAuthenticationToken(AuthenticateDTO authenticateDTO) {
        WebAuthnAuthenticationRequest authenticationRequest = new WebAuthnAuthenticationRequest(authenticateDTO.rawId().getBytes(),
                authenticateDTO.response().clientDataJSON().getBytes(), authenticateDTO.response().authenticatorData().getBytes(),
                authenticateDTO.response().signature().getBytes(), authenticateDTO.clientExtensionResults().toString());

        // or provide a collection of authorities if needed
        return new WebAuthnAuthenticationToken(authenticateDTO.id(), authenticationRequest, null // or provide a collection of authorities if needed
        );
    }

    private WebAuthnCredentialRecord getWebAuthnCredentialRecord(WebAuthnRegistrationRequestValidationResponse registrationRequestValidationResponse) {
        if (registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getAttestedCredentialData() == null) {
            throw new BadRequestAlertException("attestedCredentialData is null", ENTITY_NAME, null);
        }

        User user = userRepository.getUser();

        // TODO exchange name with passkey name
        return new WebAuthnCredentialRecordImpl(user.getName(), user.getLogin(),
                registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getAttestedCredentialData(),
                registrationRequestValidationResponse.getAttestationObject().getAttestationStatement(),
                registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getSignCount(), registrationRequestValidationResponse.getTransports(),
                registrationRequestValidationResponse.getRegistrationExtensionsClientOutputs(),
                registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getExtensions());
    }
}
