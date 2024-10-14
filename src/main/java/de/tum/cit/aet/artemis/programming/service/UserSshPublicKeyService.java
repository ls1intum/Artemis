package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.UserPublicSshKey;
import de.tum.cit.aet.artemis.programming.repository.UserPublicSshKeyRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;

@Profile(PROFILE_CORE)
@Service
public class UserSshPublicKeyService {

    private final UserPublicSshKeyRepository userPublicSshKeyRepository;

    public UserSshPublicKeyService(UserPublicSshKeyRepository userPublicSshKeyRepository) {
        this.userPublicSshKeyRepository = userPublicSshKeyRepository;
    }

    public void addSshKeyForUser(User user, AuthorizedKeyEntry keyEntry, String sshPublicKey) throws GeneralSecurityException, IOException {
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);

        UserPublicSshKey userPublicSSHKey = new UserPublicSshKey();
        userPublicSSHKey.setUserId(user.getId());
        userPublicSSHKey.setLabel("Key 1");
        userPublicSSHKey.setPublicKey(sshPublicKey);
        userPublicSSHKey.setKeyHash(keyHash);
        userPublicSSHKey.setExpiryDate(ZonedDateTime.now().plusMonths(12));
        userPublicSshKeyRepository.save(userPublicSSHKey);
    }

    public List<UserPublicSshKey> getAllSshKeysForUser(User user) {
        return userPublicSshKeyRepository.findAllByUserId(user.getId());
    }
}
