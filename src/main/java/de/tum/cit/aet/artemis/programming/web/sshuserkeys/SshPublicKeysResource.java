package de.tum.cit.aet.artemis.programming.web.sshuserkeys;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.dto.UserSshPublicKeyDTO;
import de.tum.cit.aet.artemis.programming.service.sshuserkeys.UserSshPublicKeyService;

@Profile(PROFILE_LOCALVC)
@RestController
@RequestMapping("api/programming/ssh-settings/")
public class SshPublicKeysResource {

    private static final Logger log = LoggerFactory.getLogger(SshPublicKeysResource.class);

    private final UserSshPublicKeyService userSshPublicKeyService;

    private final UserRepository userRepository;

    public SshPublicKeysResource(UserSshPublicKeyService userSshPublicKeyService, UserRepository userRepository) {
        this.userSshPublicKeyService = userSshPublicKeyService;
        this.userRepository = userRepository;
    }

    /**
     * GET public-keys : retrieves all SSH keys of a user
     *
     * @return the ResponseEntity containing all public SSH keys of a user with status 200 (OK)
     */
    @GetMapping("public-keys")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserSshPublicKeyDTO>> getSshPublicKeys() {
        User user = userRepository.getUser();
        List<UserSshPublicKeyDTO> keys = userSshPublicKeyService.getAllSshKeysForUser(user);
        return ResponseEntity.ok(keys);
    }

    /**
     * GET public-key : gets the ssh public key
     *
     * @param keyId The id of the key that should be fetched
     *
     * @return the ResponseEntity containing the requested public SSH key of a user with status 200 (OK), or with status 403 (Access Forbidden) if the key does not exist or is not
     *         owned by the requesting user
     */
    @GetMapping("public-key/{keyId}")
    @EnforceAtLeastStudent
    public ResponseEntity<UserSshPublicKeyDTO> getSshPublicKey(@PathVariable Long keyId) {
        User user = userRepository.getUser();
        UserSshPublicKey key = userSshPublicKeyService.getSshKeyForUser(user, keyId);
        return ResponseEntity.ok(UserSshPublicKeyDTO.of(key));
    }

    /**
     * POST public-key : creates a new ssh public key for a user
     *
     * @param sshPublicKey the ssh public key to create
     *
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) when the SSH key is malformed, the label is too long, or when a key with the same hash
     *         already exists
     */
    @PostMapping("public-key")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> addSshPublicKey(@RequestBody UserSshPublicKeyDTO sshPublicKey) throws GeneralSecurityException, IOException {
        User user = userRepository.getUser();
        log.debug("REST request to add SSH key to user {}", user.getLogin());
        AuthorizedKeyEntry keyEntry;
        try {
            keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(sshPublicKey.publicKey());
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Invalid SSH key format", "SSH key", "invalidKeyFormat", true);
        }
        userSshPublicKeyService.createSshKeyForUser(user, keyEntry, sshPublicKey);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete - public-key : deletes the ssh public key by its keyId
     *
     * @param keyId The id of the key that should be deleted
     *
     * @return the ResponseEntity with status 200 (OK) when the deletion succeeded, or with status 403 (Access Forbidden) if the key does not belong to the user, or does not exist
     */
    @DeleteMapping("public-key/{keyId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteSshPublicKey(@PathVariable Long keyId) {
        User user = userRepository.getUser();
        log.debug("REST request to remove SSH key of user {}", user.getLogin());
        userSshPublicKeyService.deleteUserSshPublicKey(user.getId(), keyId);

        log.debug("Successfully deleted SSH key with id {} of user {}", keyId, user.getLogin());
        return ResponseEntity.ok().build();
    }
}
