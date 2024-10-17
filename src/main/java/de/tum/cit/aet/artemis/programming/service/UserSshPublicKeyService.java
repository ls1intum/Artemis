package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
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
import de.tum.cit.aet.artemis.programming.repository.UserPublicSshKeyRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;

@Profile(PROFILE_CORE)
@Service
public class UserSshPublicKeyService {

    private final UserPublicSshKeyRepository userPublicSshKeyRepository;

    public UserSshPublicKeyService(UserPublicSshKeyRepository userPublicSshKeyRepository) {
        this.userPublicSshKeyRepository = userPublicSshKeyRepository;
    }

    public void createSshKeyForUser(User user, AuthorizedKeyEntry keyEntry, UserSshPublicKey sshPublicKey) throws GeneralSecurityException, IOException {
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);

        if (userPublicSshKeyRepository.findByKeyHash(keyHash).isPresent()) {
            throw new BadRequestAlertException("Invalid SSH key format", "SSH key", "invalidKeyFormat", true);
        }

        UserSshPublicKey newUserSshPublicKey = new UserSshPublicKey();
        newUserSshPublicKey.setUserId(user.getId());
        newUserSshPublicKey.setLabel(sshPublicKey.getLabel());
        newUserSshPublicKey.setPublicKey(sshPublicKey.getPublicKey());
        newUserSshPublicKey.setKeyHash(keyHash);
        newUserSshPublicKey.setExpiryDate(sshPublicKey.getExpiryDate());
        newUserSshPublicKey.setCreationDate(ZonedDateTime.now());
        newUserSshPublicKey.setExpiryDate(sshPublicKey.getExpiryDate());
        userPublicSshKeyRepository.save(newUserSshPublicKey);
    }

    public UserSshPublicKey getSshKeyForUser(User user, Long keyId) {
        var userSshPublicKey = userPublicSshKeyRepository.findByIdElseThrow(keyId);
        if (Objects.equals(userSshPublicKey.getUserId(), user.getId())) {
            return userSshPublicKey;
        }
        else {
            throw new EntityNotFoundException();
        }
    }

    public List<UserSshPublicKey> getAllSshKeysForUser(User user) {
        return userPublicSshKeyRepository.findAllByUserId(user.getId());
    }

    public void deleteUserSshPublicKey(Long userId, Long keyId) {
        var keys = userPublicSshKeyRepository.findAllByUserId(userId);
        if (!keys.isEmpty() && keys.stream().map(UserSshPublicKey::getId).toList().contains(keyId)) {
            userPublicSshKeyRepository.deleteById(keyId);
        }
        else {
            throw new AccessForbiddenException("SSH key", keyId);
        }
    }
}
