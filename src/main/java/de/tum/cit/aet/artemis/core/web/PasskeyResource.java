package de.tum.cit.aet.artemis.core.web;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.PasskeyEnabled;
import de.tum.cit.aet.artemis.core.config.validator.Base64Url;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.repository.passkey.ArtemisUserCredentialRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceSuperAdmin;

/**
 * REST controller for public endpoints regarding the webauthn (Web Authentication) API, e.g. used for passkeys.
 * Provides endpoints for retrieving and deleting passkeys associated with a user.
 * <p>
 * This controller is only active when the "core" profile is enabled.
 */
@Conditional(PasskeyEnabled.class)
@Lazy
@RestController
@RequestMapping("api/core/passkey/")
public class PasskeyResource {

    private static final Logger log = LoggerFactory.getLogger(PasskeyResource.class);

    private final ArtemisUserCredentialRepository artemisUserCredentialRepository;

    private final UserRepository userRepository;

    private final PasskeyCredentialsRepository passkeyCredentialsRepository;

    /**
     * @param userRepository                  for accessing user data
     * @param artemisUserCredentialRepository for managing user credentials
     */
    public PasskeyResource(ArtemisUserCredentialRepository artemisUserCredentialRepository, UserRepository userRepository,
            PasskeyCredentialsRepository passkeyCredentialsRepository) {
        this.artemisUserCredentialRepository = artemisUserCredentialRepository;
        this.userRepository = userRepository;
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
    }

    /**
     * GET /passkey/user : retrieve all passkeys for the current user
     *
     * @return list of {@link PasskeyDTO} that contains the passkeys of the current user
     */
    @GetMapping("user")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PasskeyDTO>> getPasskeys() {
        User user = userRepository.getUser();
        log.debug("Retrieving passkeys for user with id: {}", user.getId());

        List<PasskeyDTO> passkeys = artemisUserCredentialRepository.findPasskeyDtosByUserId(BytesConverter.longToBytes(user.getId()));

        return ResponseEntity.ok(passkeys);
    }

    /**
     * GET /passkey/admin : retrieve all passkeys with user information for super admin management
     *
     * @return list of {@link de.tum.cit.aet.artemis.core.dto.AdminPasskeyDTO} that contains all passkeys with user information
     */
    @GetMapping("admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<de.tum.cit.aet.artemis.core.dto.AdminPasskeyDTO>> getAllPasskeysForAdmin() {
        log.debug("Retrieving all passkeys for super admin management");

        List<de.tum.cit.aet.artemis.core.dto.AdminPasskeyDTO> passkeys = passkeyCredentialsRepository.findAll().stream().map(PasskeyCredential::toAdminDto).toList();

        return ResponseEntity.ok(passkeys);
    }

    private <T> ResponseEntity<T> logAndReturnNotFound(String credentialId) {
        log.warn("Credential with id {} not found in the repository", credentialId);
        return ResponseEntity.notFound().build();
    }

    /**
     * PUT /passkey/:credentialId : update the label of a passkey for the current user
     *
     * @param credentialId            of the passkey to be updated
     * @param passkeyWithUpdatedLabel containing the new label for the passkey
     * @return {@link ResponseEntity} with HTTP status 200 (OK) if the update is successful
     */
    @PutMapping("{credentialId}")
    @EnforceAtLeastStudent
    public ResponseEntity<PasskeyDTO> updatePasskeyLabel(@PathVariable @Base64Url String credentialId, @RequestBody PasskeyDTO passkeyWithUpdatedLabel) {
        log.debug("Updating label for passkey with id: {}", credentialId);

        User currentUser = userRepository.getUser();
        Optional<PasskeyCredential> credentialToBeUpdated = passkeyCredentialsRepository.findByCredentialId(credentialId);

        if (credentialToBeUpdated.isEmpty()) {
            return logAndReturnNotFound(credentialId);
        }

        PasskeyCredential passkeyCredential = credentialToBeUpdated.get();
        boolean isUserAllowedToUpdatePasskey = passkeyCredential.getUser().getId().equals(currentUser.getId());
        if (!isUserAllowedToUpdatePasskey) {
            log.warn("User with id {} tried to update credential with id {} of another user", currentUser.getId(), credentialId);
            return ResponseEntity.notFound().build();
        }

        passkeyCredential.setLabel(passkeyWithUpdatedLabel.label());
        PasskeyCredential updatedPasskey = passkeyCredentialsRepository.save(passkeyCredential);

        log.debug("Successfully updated label for passkey with id: {}", credentialId);
        return ResponseEntity.ok(updatedPasskey.toDto());
    }

    /**
     * DELETE /passkey/:credentialId : delete passkey with matching id for the current user
     *
     * @param credentialId of the passkey to be deleted
     * @return {@link ResponseEntity} with HTTP status 204 (No Content) if the deletion is successful
     */
    @DeleteMapping("{credentialId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deletePasskey(@PathVariable @Base64Url String credentialId) {
        log.debug("Deleting passkey with id: {}", credentialId);

        User currentUser = userRepository.getUser();
        Optional<PasskeyCredential> credentialToBeDeleted = passkeyCredentialsRepository.findByCredentialId(credentialId);

        if (credentialToBeDeleted.isEmpty()) {
            return logAndReturnNotFound(credentialId);
        }

        boolean isUserAllowedToDeletePasskey = credentialToBeDeleted.get().getUser().getId().equals(currentUser.getId());
        if (!isUserAllowedToDeletePasskey) {
            log.warn("User with id {} tried to delete credential with id {} of other user", currentUser.getId(), credentialId);
            return ResponseEntity.notFound().build();
        }

        artemisUserCredentialRepository.delete(Bytes.fromBase64(credentialId));
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /passkey/:credentialId/approval : update the super admin approval status of a passkey
     *
     * @param credentialId               of the passkey to be updated
     * @param passkeyWithUpdatedApproval containing the new approval status for the passkey
     * @return {@link ResponseEntity} with HTTP status 200 (OK) and the updated passkey if successful
     */
    @PutMapping("{credentialId}/approval")
    @EnforceSuperAdmin
    public ResponseEntity<PasskeyDTO> updatePasskeyApproval(@PathVariable @Base64Url String credentialId, @RequestBody PasskeyDTO passkeyWithUpdatedApproval) {
        log.debug("Updating approval status for passkey with id: {}", credentialId);

        Optional<PasskeyCredential> credentialToBeUpdated = passkeyCredentialsRepository.findByCredentialId(credentialId);

        if (credentialToBeUpdated.isEmpty()) {
            return logAndReturnNotFound(credentialId);
        }

        PasskeyCredential passkeyCredential = credentialToBeUpdated.get();
        passkeyCredential.setSuperAdminApproved(passkeyWithUpdatedApproval.isSuperAdminApproved());
        PasskeyCredential updatedPasskey = passkeyCredentialsRepository.save(passkeyCredential);

        log.debug("Successfully updated approval status for passkey with id: {}", credentialId);
        return ResponseEntity.ok(updatedPasskey.toDto());
    }
}
