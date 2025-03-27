package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URISyntaxException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webauthn4j.springframework.security.WebAuthnRegistrationRequestValidationResponse;
import com.webauthn4j.springframework.security.WebAuthnRegistrationRequestValidator;
import com.webauthn4j.springframework.security.exception.WebAuthnAuthenticationException;
import com.webauthn4j.util.exception.WebAuthnException;

import de.tum.cit.aet.artemis.core.dto.CreatePasskeyDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;

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

    @Autowired
    public PublicWebauthnResource(WebAuthnRegistrationRequestValidator registrationRequestValidator) {
        this.registrationRequestValidator = registrationRequestValidator;
    }

    /**
     * {@code POST /signup} : register a new passkey for a user.
     *
     * @return ResponseEntity with status 201 (Created) if the passkey was successfully registered.
     */
    @PostMapping("signup")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> registerNewPasskey(HttpServletRequest request, @Valid @RequestBody CreatePasskeyDTO createPasskeyDTO, BindingResult result)
            throws URISyntaxException {

        if (result.hasErrors()) {
            log.error("Validation errors: {}", result.getAllErrors());
            throw new BadRequestAlertException("User input validation failed. Please try again.", ENTITY_NAME, "TODO");
        }

        WebAuthnRegistrationRequestValidationResponse registrationRequestValidationResponse;
        try {
            registrationRequestValidationResponse = registrationRequestValidator.validate(request, createPasskeyDTO.clientDataJSON(), createPasskeyDTO.attestationObject(),
                    createPasskeyDTO.transports(), createPasskeyDTO.clientExtensions());
        }
        catch (WebAuthnException | WebAuthnAuthenticationException ignored) {
            throw new BadRequestAlertException("WebAuthn registration request validation failed. Please try again.", ENTITY_NAME, "TODO");
        }

        return ResponseEntity.ok().build();
    }

    // @PostMapping(value = "/signup")
    // public String create(HttpServletRequest request, @Valid @ModelAttribute("userForm") UserCreateForm userCreateForm, BindingResult result, Model model, RedirectAttributes
    // redirectAttributes) {
    //
    // var username = userCreateForm.getUsername();
    //
    // var authenticator = new WebAuthnAuthenticatorImpl(
    // "authenticator",
    // username,
    // registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getAttestedCredentialData(),
    // registrationRequestValidationResponse.getAttestationObject().getAttestationStatement(),
    // registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getSignCount(),
    // registrationRequestValidationResponse.getTransports(),
    // registrationRequestValidationResponse.getRegistrationExtensionsClientOutputs(),
    // registrationRequestValidationResponse.getAttestationObject().getAuthenticatorData().getExtensions()
    // );
    //
    // try {
    // webAuthnAuthenticatorManager.createAuthenticator(authenticator);
    // } catch (IllegalArgumentException ex) {
    // model.addAttribute("errorMessage", "Registration failed. The user may already be registered.");
    // logger.error("Registration failed.", ex);
    // return VIEW_LOGIN;
    // }
    // } catch (RuntimeException ex) {
    // model.addAttribute("errorMessage", "Registration failed by unexpected error.");
    // logger.error("Registration failed.", ex);
    // return VIEW_LOGIN;
    // }
    //
    // model.addAttribute("successMessage", "User registration successful. Please login.");
    // return VIEW_LOGIN;
    // }
}
