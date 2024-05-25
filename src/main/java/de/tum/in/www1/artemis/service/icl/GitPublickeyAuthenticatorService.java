package de.tum.in.www1.artemis.service.icl;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.security.PublicKey;

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.icl.ssh.HashUtils;
import de.tum.in.www1.artemis.config.icl.ssh.SshConstants;
import de.tum.in.www1.artemis.repository.UserRepository;

@Profile(PROFILE_LOCALVC)
@Service
public class GitPublickeyAuthenticatorService implements PublickeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(GitPublickeyAuthenticatorService.class);

    private final UserRepository userRepository;

    public GitPublickeyAuthenticatorService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean authenticate(String username, PublicKey publicKey, ServerSession session) {
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        var user = userRepository.findBySshPublicKeyHash(keyHash);
        if (user.isPresent()) {
            log.info("Found user {} for public key authentication", user.get().getLogin());
            session.setAttribute(SshConstants.USER_KEY, user.get());
            return true;
        }
        return false;
    }
}
