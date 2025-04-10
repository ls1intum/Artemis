package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.ws.rs.NotAllowedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.dto.PasskeyDto;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.repository.webauthn.ArtemisUserCredentialRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

/**
 * REST controller for public endpoints regarding the webauthn (Web Authentication) API, e.g. used for passkeys.
 * Provides endpoints for retrieving and deleting passkeys associated with a user.
 *
 * This controller is only active when the "core" profile is enabled.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/passkey/")
public class PasskeyResource {

    private static final Logger log = LoggerFactory.getLogger(PasskeyResource.class);

    private final UserRepository userRepository;

    private final ArtemisUserCredentialRepository artemisUserCredentialRepository;

    @Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}")
    public boolean enabled;

    /**
     * @param userRepository                  for accessing user data
     * @param artemisUserCredentialRepository for managing user credentials
     */
    public PasskeyResource(UserRepository userRepository, ArtemisUserCredentialRepository artemisUserCredentialRepository) {
        this.userRepository = userRepository;
        this.artemisUserCredentialRepository = artemisUserCredentialRepository;
    }

    /**
     * Checks if the passkey feature is enabled.
     *
     * @throws NotAllowedException if the passkey feature is not enabled
     */
    private void checkIfPasskeyFeatureIsEnabled() throws NotAllowedException {
        if (!enabled) {
            log.info("If you want to enable the passkey feature, please set the property '{}' to true in your application properties.", Constants.PASSKEY_ENABLED_PROPERTY_NAME);
            throw new NotAllowedException("Passkey feature is not enabled");
        }
    }

    @GetMapping("user")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PasskeyDto>> getPasskeys() {
        checkIfPasskeyFeatureIsEnabled();

        User user = userRepository.getUser();
        log.info("Retrieving passkeys for user with id: {}", user.getId());

        List<PasskeyDto> passkeys = artemisUserCredentialRepository.findPasskeyDtosByUserId(BytesConverter.longToBytes(user.getId()));

        return ResponseEntity.ok(passkeys);
    }

    /**
     * Deletes a passkey associated with the given credential ID.
     *
     * This endpoint allows users to delete a specific passkey by providing its
     * Base64-encoded credential ID. The passkey feature must be enabled for this
     * operation to succeed.
     *
     * @param credentialIdBase64Encoded of the passkey to delete
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content) if the deletion is successful
     * @throws NotAllowedException if the passkey feature is disabled
     */
    @DeleteMapping("{credentialIdBase64Encoded}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deletePasskey(@PathVariable String credentialIdBase64Encoded) {
        log.info("Deleting passkey with id: {}", credentialIdBase64Encoded);
        checkIfPasskeyFeatureIsEnabled();

        Bytes credentialId = Bytes.fromBase64(credentialIdBase64Encoded);
        artemisUserCredentialRepository.delete(credentialId);

        return ResponseEntity.noContent().build();
    }
}
