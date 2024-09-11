package de.tum.cit.aet.artemis.service.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.icl.ssh.HashUtils;
import de.tum.cit.aet.artemis.programming.icl.ssh.SshConstants;
import de.tum.cit.aet.artemis.service.connectors.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.service.connectors.localci.dto.BuildAgentInformation;

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
            try {
                // Retrieve the stored public key string
                String storedPublicKeyString = user.get().getSshPublicKey();

                // Parse the stored public key string
                AuthorizedKeyEntry keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(storedPublicKeyString);
                PublicKey storedPublicKey = keyEntry.resolvePublicKey(null, null, null);

                // Compare the stored public key with the provided public key
                if (Objects.equals(storedPublicKey, publicKey)) {
                    log.debug("Found user {} for public key authentication", user.get().getLogin());
                    session.setAttribute(SshConstants.USER_KEY, user.get());
                    session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, false);
                    return true;
                }
                else {
                    log.warn("Public key mismatch for user {}", user.get().getLogin());
                }
            }
            catch (Exception e) {
                log.error("Failed to convert stored public key string to PublicKey object", e);
            }
        }
        else if (localCIBuildJobQueueService.isPresent()
                && localCIBuildJobQueueService.get().getBuildAgentInformation().stream().anyMatch(agent -> checkPublicKeyMatchesBuildAgentPublicKey(agent, publicKey))) {
            log.info("Authenticating as build agent");
            session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, true);
            return true;
        }
        return false;
    }

    private boolean checkPublicKeyMatchesBuildAgentPublicKey(BuildAgentInformation agent, PublicKey publicKey) {
        if (agent.publicSshKey() == null) {
            return false;
        }

        AuthorizedKeyEntry agentKeyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(agent.publicSshKey());
        PublicKey agentPublicKey;
        try {
            agentPublicKey = agentKeyEntry.resolvePublicKey(null, null, null);
        }
        catch (IOException | GeneralSecurityException e) {
            return false;
        }

        return agentPublicKey.equals(publicKey);
    }
}
