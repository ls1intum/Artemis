package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.core.config.validator.Base64Url;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
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

    private final ArtemisUserCredentialRepository artemisUserCredentialRepository;

    private final boolean enabled;

    private final UserRepository userRepository;

    private final PasskeyCredentialsRepository passkeyCredentialsRepository;

    /**
     * @param userRepository                  for accessing user data
     * @param artemisUserCredentialRepository for managing user credentials
     */
    public PasskeyResource(ArtemisUserCredentialRepository artemisUserCredentialRepository, @Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}") boolean enabled,
            UserRepository userRepository, PasskeyCredentialsRepository passkeyCredentialsRepository) {
        this.artemisUserCredentialRepository = artemisUserCredentialRepository;
        this.enabled = enabled;
        this.userRepository = userRepository;
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
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

    /**
     * GET /passkey/user : retrieve all passkeys for the current user
     *
     * @return list of {@link PasskeyDTO} that contains the passkeys of the current user
     */
    @GetMapping("user")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PasskeyDTO>> getPasskeys() {
        checkIfPasskeyFeatureIsEnabled();

        User user = userRepository.getUser();
        log.info("Retrieving passkeys for user with id: {}", user.getId());

        List<PasskeyDTO> passkeys = artemisUserCredentialRepository.findPasskeyDtosByUserId(BytesConverter.longToBytes(user.getId()));

        return ResponseEntity.ok(passkeys);
    }

    /**
     * DELETE /passkey/:credentialId : delete passkey with matching id for current user
     *
     * @param credentialId of the passkey to be deleted
     * @return {@link ResponseEntity} with HTTP status 204 (No Content) if the deletion is successful
     */
    @DeleteMapping("{credentialId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deletePasskey(@PathVariable @Base64Url String credentialId) {
        log.info("Deleting passkey with id: {}", credentialId);
        checkIfPasskeyFeatureIsEnabled();

        User currentUser = userRepository.getUser();
        Optional<PasskeyCredential> credentialToBeDeleted = passkeyCredentialsRepository.findByCredentialId(credentialId);

        if (credentialToBeDeleted.isPresent()) {
            boolean isUserAllowedToDeletePasskey = credentialToBeDeleted.get().getUser().getId().equals(currentUser.getId());
            if (!isUserAllowedToDeletePasskey) {
                log.warn("User {} tried to delete credential with id {} of other user", credentialId, currentUser.getId());
                return ResponseEntity.notFound().build();
            }
        }
        else {
            log.warn("Credential with id {} not found in the repository", credentialId);
            return ResponseEntity.notFound().build();
        }

        artemisUserCredentialRepository.delete(Bytes.fromBase64(credentialId));
        return ResponseEntity.noContent().build();
    }
}
