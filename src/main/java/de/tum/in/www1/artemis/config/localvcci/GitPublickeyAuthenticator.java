package de.tum.in.www1.artemis.config.localvcci;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class GitPublickeyAuthenticator implements PublickeyAuthenticator {

    private final UserRepository userRepository;

    // Constructor injection for UserRepository
    public GitPublickeyAuthenticator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean authenticate(String username, PublicKey key, ServerSession session) {

        try {
            String keyString = PublicKeyUtils.encodePublicKey(key);
            String keyHash = HashUtils.hashString(keyString);
            var user = userRepository.findBySshPublicKey(keyHash);
            if (user.isPresent()) {
                session.setAuthenticated();
                session.setAttribute(SSHDConstants.USER_KEY, user.get());
                return true;
            }
        }
        catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
