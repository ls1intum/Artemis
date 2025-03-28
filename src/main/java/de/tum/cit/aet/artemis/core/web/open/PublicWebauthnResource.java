package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URISyntaxException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.springframework.security.WebAuthnRegistrationRequestValidationResponse;
import com.webauthn4j.springframework.security.WebAuthnRegistrationRequestValidator;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecord;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordImpl;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordManager;
import com.webauthn4j.springframework.security.exception.WebAuthnAuthenticationException;
import com.webauthn4j.util.exception.WebAuthnException;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CreatePasskeyDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;

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

    public PublicWebauthnResource(WebAuthnRegistrationRequestValidator registrationRequestValidator, WebAuthnCredentialRecordManager webAuthnAuthenticatorManager,
            UserRepository userRepository) {
        this.registrationRequestValidator = registrationRequestValidator;
        this.webAuthnAuthenticatorManager = webAuthnAuthenticatorManager;
        this.userRepository = userRepository;
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

        String clientExtensionResultsJson = null;
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
            // TODO check data from the demo and diff to what we sent from the client right now
            registrationRequestValidationResponse = registrationRequestValidator.validate(request, createPasskeyDTO.webAuthnCredential().response().clientDataJSON(),
                    createPasskeyDTO.webAuthnCredential().response().attestationObject(), createPasskeyDTO.webAuthnCredential().response().transports(),
                    clientExtensionResultsJson);
        }
        catch (WebAuthnException | WebAuthnAuthenticationException ignored) {
            throw new BadRequestAlertException("WebAuthn registration request validation failed. Please try again.", ENTITY_NAME, "TODO");
        }

        WebAuthnCredentialRecord authenticator = getWebAuthnCredentialRecord(registrationRequestValidationResponse);

        try {
            webAuthnAuthenticatorManager.createCredentialRecord(authenticator);
        }
        catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException("Passkey registration failed", ENTITY_NAME, null);
        }

        // TODO would need to define a URI for created, does that make sense here? Which URI would it be?
        // return ResponseEntity.created().build();
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST /authenticate} : authenticate as a user with a passkey.
     *
     * @return ResponseEntity with status 201 (Created) if successfully authenticated.
     */
    @PostMapping("authenticate")
    @EnforceNothing
    public ResponseEntity<Void> authenticate(HttpServletRequest request, @Valid @RequestBody CreatePasskeyDTO createPasskeyDTO, BindingResult result) throws URISyntaxException {

        // TODO
        return ResponseEntity.ok().build();
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
