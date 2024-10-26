package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.dto.UserSshPublicKeyDTO;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;

@Profile(PROFILE_CORE)
@Service
public class UserSshPublicKeyService {

    private static final String KEY_DEFAULT_LABEL = "Key 1";

    private final UserSshPublicKeyRepository userSshPublicKeyRepository;

    public UserSshPublicKeyService(UserSshPublicKeyRepository userSshPublicKeyRepository) {
        this.userSshPublicKeyRepository = userSshPublicKeyRepository;
    }

    /**
     * Creates a new SSH public key for the specified user, ensuring that the key is unique
     * based on its SHA-512 hash fingerprint. If the key already exists, an exception is thrown.
     *
     * @param user         the {@link User} for whom the SSH key is being created.
     * @param keyEntry     the {@link AuthorizedKeyEntry} containing the SSH public key details, used to resolve the {@link PublicKey}.
     * @param sshPublicKey the {@link UserSshPublicKey} object containing metadata about the SSH key such as the key itself, label, and expiry date.
     */
    public void createSshKeyForUser(User user, AuthorizedKeyEntry keyEntry, UserSshPublicKeyDTO sshPublicKey) throws GeneralSecurityException, IOException {
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);

        if (userSshPublicKeyRepository.findByKeyHash(keyHash).isPresent()) {
            throw new BadRequestAlertException("Key already exists", "SSH key", "keyAlreadyExists", true);
        }

        UserSshPublicKey newUserSshPublicKey = new UserSshPublicKey();
        newUserSshPublicKey.setUserId(user.getId());
        newUserSshPublicKey.setPublicKey(sshPublicKey.publicKey());
        newUserSshPublicKey.setKeyHash(keyHash);
        setLabelForKey(newUserSshPublicKey, sshPublicKey.label());
        newUserSshPublicKey.setCreationDate(ZonedDateTime.now());
        newUserSshPublicKey.setExpiryDate(sshPublicKey.expiryDate());
        userSshPublicKeyRepository.save(newUserSshPublicKey);
    }

    /**
     * Sets the label for the provided SSH public key. If the given label is null or empty,
     * the label is extracted from the public key or defaults to a predefined value.
     *
     * @param newSshPublicKey the {@link UserSshPublicKey} for which the label is being set.
     * @param label           the label to assign to the SSH key, or null/empty to use the default logic.
     * @throws BadRequestAlertException if the key label is longer than 50 characters
     */
    public void setLabelForKey(UserSshPublicKey newSshPublicKey, String label) {
        if (label == null || label.isEmpty()) {
            String[] parts = newSshPublicKey.getPublicKey().split("\\s+");
            if (parts.length >= 3) {
                label = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            }
            else {
                label = KEY_DEFAULT_LABEL;
            }
        }
        if (label.length() <= 50) {
            newSshPublicKey.setLabel(label);
        }
        else {
            throw new BadRequestAlertException("Key label is too long", "SSH key", "keyLabelTooLong", true);
        }
    }

    /**
     * Retrieves the SSH public key for the specified user by key ID.
     *
     * @param user  the {@link User} to whom the SSH key belongs.
     * @param keyId the ID of the SSH key.
     * @return the {@link UserSshPublicKey} if found and belongs to the user.
     * @throws EntityNotFoundException if the key does not belong to the user.
     */
    public UserSshPublicKey getSshKeyForUser(User user, Long keyId) {
        var userSshPublicKey = userSshPublicKeyRepository.findByIdElseThrow(keyId);
        if (Objects.equals(userSshPublicKey.getUserId(), user.getId())) {
            return userSshPublicKey;
        }
        else {
            throw new AccessForbiddenException("SSH key", keyId);
        }
    }

    /**
     * Retrieves all SSH public keys associated with the specified user.
     *
     * @param user the {@link User} whose SSH keys are to be retrieved.
     * @return a list of {@link UserSshPublicKey} objects for the user.
     */
    public List<UserSshPublicKey> getAllSshKeysForUser(User user) {
        return userSshPublicKeyRepository.findAllByUserId(user.getId());
    }

    /**
     * Deletes the specified SSH public key for the given user ID.
     *
     * @param userId the ID of the user.
     * @param keyId  the ID of the SSH key to delete.
     * @throws AccessForbiddenException if the key does not belong to the user.
     */
    public void deleteUserSshPublicKey(Long userId, Long keyId) {
        if (userSshPublicKeyRepository.existsByIdAndUserId(keyId, userId)) {
            userSshPublicKeyRepository.deleteById(keyId);
        }
        else {
            throw new AccessForbiddenException("SSH key", keyId);
        }
    }

    /**
     * Returns whether the user of the specified id has stored SSH keys
     *
     * @param userId the ID of the user.
     * @return true if the user has SSH keys, false if not
     */
    public boolean hasUserSSHkeys(Long userId) {
        return userSshPublicKeyRepository.existsByUserId(userId);
    }
}
