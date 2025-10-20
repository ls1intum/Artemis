package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.net.InetSocketAddress;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.RateLimitService;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshConstants;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class GitPublickeyAuthenticatorService implements PublickeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(GitPublickeyAuthenticatorService.class);

    private final UserRepository userRepository;

    private final Optional<DistributedDataAccessService> localCIDistributedDataAccessService;

    private final UserSshPublicKeyRepository userSshPublicKeyRepository;

    private final RateLimitService rateLimitService;

    private static final int AUTHENTICATION_FAILED_CODE = 10;

    @Value("${server.url}")
    private String artemisServerUrl;

    public GitPublickeyAuthenticatorService(UserRepository userRepository, Optional<DistributedDataAccessService> localCIDistributedDataAccessService,
            UserSshPublicKeyRepository userSshPublicKeyRepository, RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.localCIDistributedDataAccessService = localCIDistributedDataAccessService;
        this.userSshPublicKeyRepository = userSshPublicKeyRepository;
        this.rateLimitService = rateLimitService;
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
            else {
                disconnectBecauseKeyHasExpired(session);
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
    private boolean authenticateUser(UserSshPublicKey storedKey, PublicKey providedKey, ServerSession session) {
        try {
            String ipString = ((InetSocketAddress) session.getRemoteAddress()).getHostString();
            final IPAddress ipAddress = new IPAddressString(ipString).getAddress();

            rateLimitService.enforcePerMinute(ipAddress, RateLimitType.LOGIN_RELATED);
        }
        catch (RuntimeException e) {
            log.warn("Rate limit exceeded for SSH authentication from {}", session.getRemoteAddress(), e);
            return false;
        }

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
        if (localCIDistributedDataAccessService.isPresent()) {
            // Find the build agent that matches the provided key
            Optional<BuildAgentInformation> matchingAgent = localCIDistributedDataAccessService.get().getBuildAgentInformation().stream()
                    .filter(agent -> checkPublicKeyMatchesBuildAgentPublicKey(agent, providedKey)).findFirst();

            if (matchingAgent.isPresent()) {
                var agent = matchingAgent.get().buildAgent();
                log.debug("Authenticating build agent {} on address {}", agent.displayName(), agent.memberAddress());
                session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, true);
                return true;
            }
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

    /**
     * Disconnects the client from the session and informs that the key used to authenticate with has expired
     *
     * @param session the session with the client
     */
    private void disconnectBecauseKeyHasExpired(ServerSession session) {
        try {
            var keyExpiredErrorMessage = String.format("""
                    Keys expired.

                    One of your SSH keys has expired. Renew it in the Artemis settings:
                    %s/user-settings/ssh
                    """, artemisServerUrl);

            session.disconnect(AUTHENTICATION_FAILED_CODE, keyExpiredErrorMessage);
        }
        catch (IOException e) {
            log.info("Failed to disconnect SSH client session {}", e.getMessage());
        }
    }
}
