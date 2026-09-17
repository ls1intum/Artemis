package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localvc.service.ssh.HashUtils;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
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

    private final BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    private final Optional<BuildAgentAddressRegistryService> buildAgentAddressRegistryService;

    private static final int AUTHENTICATION_FAILED_CODE = 10;

    @Value("${server.url}")
    private String artemisServerUrl;

    public GitPublickeyAuthenticatorService(UserRepository userRepository, Optional<DistributedDataAccessService> localCIDistributedDataAccessService,
            UserSshPublicKeyRepository userSshPublicKeyRepository, RateLimitService rateLimitService, BuildAgentNetworkPolicy buildAgentNetworkPolicy,
            Optional<BuildAgentAddressRegistryService> buildAgentAddressRegistryService) {
        this.userRepository = userRepository;
        this.localCIDistributedDataAccessService = localCIDistributedDataAccessService;
        this.userSshPublicKeyRepository = userSshPublicKeyRepository;
        this.rateLimitService = rateLimitService;
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
        this.buildAgentAddressRegistryService = buildAgentAddressRegistryService;
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
            // getClientAddress rather than getRemoteAddress: behind a load balancer the latter is the balancer, so
            // every user would share one rate limit bucket. ProxyProtocolAcceptor fills in the real client where the
            // balancer announces it, and where it does not the two are the same address anyway.
            String ipString = ((InetSocketAddress) session.getClientAddress()).getHostString();
            final IPAddress ipAddress = new IPAddressString(ipString).getAddress();

            rateLimitService.enforcePerMinute(ipAddress, RateLimitType.AUTHENTICATION);
        }
        catch (RuntimeException e) {
            log.warn("Rate limit exceeded for SSH authentication from {}", session.getClientAddress(), e);
            return false;
        }

        try {
            var storedKeyOwner = userRepository.findById(storedKey.getUserId());
            if (storedKeyOwner.isEmpty()) {
                return false;
            }
            User user = storedKeyOwner.get();
            // An SSH key is a credential of its own: nothing else on this path consults account state, so without this a
            // deactivated or soft-deleted user could still read and write their repositories with a key issued earlier.
            if (!user.getActivated() || user.isDeleted()) {
                log.warn("SSH authentication attempt for user {} whose account is deactivated or deleted", user.getLogin());
                return false;
            }
            // Retrieve and parse the stored public key string
            AuthorizedKeyEntry keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(storedKey.getPublicKey());
            PublicKey storedPublicKey = keyEntry.resolvePublicKey(null, null, null);

            // Compare the stored public key with the provided public key
            if (Objects.equals(storedPublicKey, providedKey)) {
                log.debug("Found user {} for public key authentication", user.getLogin());
                session.setAttribute(SshConstants.USER_KEY, user);
                session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, false);
                return true;
            }
            else {
                log.warn("Public key mismatch for user {}", user.getLogin());
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

                // The key proves which agent this is; the address decides whether that agent may act from here. Behind
                // a load balancer this is the client address recovered from the PROXY protocol header rather than the
                // balancer, which is the whole reason ProxyProtocolAcceptor exists.
                String clientAddress = hostOf(session.getClientAddress());
                if (clientAddress == null) {
                    // No usable address means the origin cannot be established. Refuse rather than fall through to the
                    // checks below, both of which answer "yes" when they have nothing to constrain: an unconfigured
                    // allowlist permits everything, and the registry permits everything while it cannot observe.
                    log.warn("Refusing build agent {} because its client address could not be determined from {}", agent.name(), session.getClientAddress());
                    return false;
                }
                if (!buildAgentNetworkPolicy.isWithinAllowedRanges(clientAddress)) {
                    log.warn("Refusing build agent {} authenticating from {}, which is outside the configured build agent networks", agent.name(), clientAddress);
                    return false;
                }
                if (buildAgentAddressRegistryService.isPresent() && !buildAgentAddressRegistryService.get().isRegisteredAddressOfAgent(agent.name(), clientAddress)) {
                    log.warn("Refusing a key of build agent {} presented from {}, which is not an address that agent is connected from", agent.name(), clientAddress);
                    return false;
                }

                log.debug("Authenticating build agent {} on address {}", agent.displayName(), agent.memberAddress());
                session.setAttribute(SshConstants.IS_BUILD_AGENT_KEY, true);
                // Recorded so the repository check can scope this session to the jobs this agent is actually running
                session.setAttribute(SshConstants.BUILD_AGENT_NAME_KEY, agent.name());
                return true;
            }
        }
        return false;
    }

    private static String hostOf(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        return null;
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
            var keyExpiredErrorMessage = """
                    Keys expired.

                    One of your SSH keys has expired. Renew it in the Artemis settings:
                    %s/user-settings/ssh
                    """.formatted(artemisServerUrl);

            session.disconnect(AUTHENTICATION_FAILED_CODE, keyExpiredErrorMessage);
        }
        catch (IOException e) {
            log.info("Failed to disconnect SSH client session {}", e.getMessage());
        }
    }
}
