package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshConstants;

@Profile(PROFILE_LOCALVC)
@Service
public class GitPublickeyAuthenticatorService implements PublickeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(GitPublickeyAuthenticatorService.class);

    private final UserRepository userRepository;

    private final Optional<SharedQueueManagementService> localCIBuildJobQueueService;

    private final UserSshPublicKeyRepository userSshPublicKeyRepository;

    public GitPublickeyAuthenticatorService(UserRepository userRepository, Optional<SharedQueueManagementService> localCIBuildJobQueueService,
            UserSshPublicKeyRepository userSshPublicKeyRepository) {
        this.userRepository = userRepository;
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
        this.userSshPublicKeyRepository = userSshPublicKeyRepository;
    }

    @Override
    public boolean authenticate(String username, PublicKey publicKey, ServerSession session) {
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        var userSshPublicKey = userSshPublicKeyRepository.findByKeyHash(keyHash);
        return userSshPublicKey.map(sshPublicKey -> {
            ZonedDateTime expiryDate = sshPublicKey.getExpiryDate();
            if (expiryDate == null || expiryDate.isAfter(ZonedDateTime.now())) {
                return authenticateUser(sshPublicKey, publicKey, session);
            }
            return false;
        }).orElseGet(() -> authenticateBuildAgent(publicKey, session));
    }

    /**
     * Tries to authenticate a user by the provided key
     *
     * @param storedKey   The key stored in the Artemis database
     * @param providedKey The key provided by the user for authentication
     * @param session     The SSH server session
     *
     * @return true if the authentication succeeds, and false if it doesn't
     */
    public boolean authenticateUser(UserSshPublicKey storedKey, PublicKey providedKey, ServerSession session) {
        try {
            var user = userRepository.findById(storedKey.getUserId());
            if (user.isEmpty()) {
                return false;
            }
            // Retrieve and parse the stored public key string
            AuthorizedKeyEntry keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(storedKey.getPublicKey());
            PublicKey storedPublicKey = keyEntry.resolvePublicKey(null, null, null);

            // Compare the stored public key with the provided public key
            if (Objects.equals(storedPublicKey, providedKey)) {
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
        return false;
    }

    /**
     * Tries to authenticate a build agent by the provided key
     *
     * @param providedKey The key provided by the user for authentication
     * @param session     The SSH server session
     *
     * @return true if the authentication succeeds, and false if it doesn't
     */
    private boolean authenticateBuildAgent(PublicKey providedKey, ServerSession session) {
        if (localCIBuildJobQueueService.isPresent()
                && localCIBuildJobQueueService.get().getBuildAgentInformation().stream().anyMatch(agent -> checkPublicKeyMatchesBuildAgentPublicKey(agent, providedKey))) {

            log.info("Authenticating as build agent");
            session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, true);
            return true;
        }
        return false;
    }

    /**
     * Checks whether a provided key matches the build agents public key
     *
     * @param agent     The build agent which tires to be authenticated by Artemis
     * @param publicKey The provided public key
     *
     * @return true if the build agents has this public key, and false if it doesn't
     */
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
