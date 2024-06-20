package de.tum.in.www1.artemis.service.icl;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.security.PublicKey;
import java.util.Optional;

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.icl.ssh.HashUtils;
import de.tum.in.www1.artemis.config.icl.ssh.SshConstants;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.localci.SharedQueueManagementService;

@Profile(PROFILE_LOCALVC)
@Service
public class GitPublickeyAuthenticatorService implements PublickeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(GitPublickeyAuthenticatorService.class);

    private final UserRepository userRepository;

    private final Optional<SharedQueueManagementService> localCIBuildJobQueueService;

    public GitPublickeyAuthenticatorService(UserRepository userRepository, Optional<SharedQueueManagementService> localCIBuildJobQueueService) {
        this.userRepository = userRepository;
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
    }

    @Override
    public boolean authenticate(String username, PublicKey publicKey, ServerSession session) {
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        var user = userRepository.findBySshPublicKeyHash(keyHash);
        if (user.isPresent()) {
            log.info("Found user {} for public key authentication", user.get().getLogin());
            session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, false);
            session.setAttribute(SshConstants.USER_KEY, user.get());
            return true;
        }
        else if (localCIBuildJobQueueService.isPresent() && localCIBuildJobQueueService.orElseThrow().getBuildAgentInformation().stream().anyMatch(agent -> {
            if (agent.publicSshKeyHash() == null) {
                return false;
            }
            return agent.publicSshKeyHash().equals(keyHash);
        })) {
            log.info("Authenticating as build agent");
            session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, true);
            return true;
        }
        return false;
    }
}
