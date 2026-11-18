package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

/**
 * The origin half of build agent ssh authentication.
 * <p>
 * A key proves <em>which</em> agent is connecting and nothing about <em>from where</em>, which is the gap this closes:
 * a key recovered from a build host would otherwise authenticate that agent from anywhere the ssh port is reachable.
 * The repository scoping that follows is covered by {@link SshBuildAgentJobScopingTest}; what is asserted here is that
 * an agent is only accepted from an address it is observed to be connected from, and inside the configured networks.
 * <p>
 * Every refusal below is a {@code false} rather than an exception, because that is how
 * {@code PublickeyAuthenticator} declines: the client is simply not authenticated by this key.
 */
class SshBuildAgentOriginTest {

    private static final String AGENT_NAME = "artemis-build-agent-1";

    private static final String AGENT_ADDRESS = "10.0.0.5";

    private DistributedDataAccessService distributedDataAccessService;

    private BuildAgentAddressRegistryService buildAgentAddressRegistryService;

    private PublicKey agentPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        agentPublicKey = keyPair.getPublic();

        distributedDataAccessService = mock(DistributedDataAccessService.class);
        buildAgentAddressRegistryService = mock(BuildAgentAddressRegistryService.class);
        var agent = new BuildAgentInformation(new BuildAgentDTO(AGENT_NAME, "[10.0.0.5]:5701", AGENT_NAME), 1, 0, List.of(), BuildAgentStatus.IDLE,
                PublicKeyEntry.toString(agentPublicKey), null, 0);
        when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(List.of(agent));
        // The agent is observed where it says it is, unless a test says otherwise
        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, AGENT_ADDRESS)).thenReturn(true);
    }

    private GitPublickeyAuthenticatorService createService(List<String> allowedRanges) {
        var configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(allowedRanges);
        var userSshPublicKeyRepository = mock(UserSshPublicKeyRepository.class);
        // No user owns this key, so authentication falls through to the build agent branch
        when(userSshPublicKeyRepository.findByKeyHash(any())).thenReturn(Optional.empty());
        return new GitPublickeyAuthenticatorService(mock(UserRepository.class), Optional.of(distributedDataAccessService), userSshPublicKeyRepository, mock(RateLimitService.class),
                new BuildAgentNetworkPolicy(configuration, new MockEnvironment()), Optional.of(buildAgentAddressRegistryService));
    }

    private ServerSession sessionFrom(String clientAddress) {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(clientAddress == null ? null : new InetSocketAddress(clientAddress, 55555));
        return session;
    }

    @Test
    void shouldAuthenticateAnAgentFromAnAddressItIsConnectedFrom() {
        ServerSession session = sessionFrom(AGENT_ADDRESS);

        assertThat(createService(List.of()).authenticate(AGENT_NAME, agentPublicKey, session)).isTrue();

        verify(session).setAttribute(SshConstants.IS_BUILD_AGENT_KEY, true);
        // Recorded from the key match rather than claimed by the client, and it is what scopes the session to this
        // agent's own build jobs
        verify(session).setAttribute(SshConstants.BUILD_AGENT_NAME_KEY, AGENT_NAME);
    }

    /**
     * The case the origin check exists for: the right key, presented from somewhere the agent is not.
     */
    @Test
    void shouldRefuseAnAgentKeyFromAnAddressItIsNotConnectedFrom() {
        assertThat(createService(List.of()).authenticate(AGENT_NAME, agentPublicKey, sessionFrom("203.0.113.9"))).isFalse();
    }

    @Test
    void shouldRefuseAnAgentOutsideTheConfiguredNetworks() {
        // Observed there and still refused: the allowlist bounds which hosts may act as a build agent at all
        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, "203.0.113.9")).thenReturn(true);

        assertThat(createService(List.of("10.0.0.0/8")).authenticate(AGENT_NAME, agentPublicKey, sessionFrom("203.0.113.9"))).isFalse();
    }

    @Test
    void shouldAuthenticateAnAgentInsideTheConfiguredNetworks() {
        assertThat(createService(List.of("10.0.0.0/8")).authenticate(AGENT_NAME, agentPublicKey, sessionFrom(AGENT_ADDRESS))).isTrue();
    }

    /**
     * No usable address means the origin cannot be established at all, which has to refuse rather than fall through:
     * both checks below it answer "yes" when they have nothing to constrain - an unconfigured allowlist permits
     * everything, and the registry permits everything for an agent it cannot observe.
     */
    @Test
    void shouldRefuseWhenTheClientAddressCannotBeDetermined() {
        assertThat(createService(List.of()).authenticate(AGENT_NAME, agentPublicKey, sessionFrom(null))).isFalse();
    }

    /**
     * A key that belongs to no registered agent is not an agent key, whatever address it comes from.
     */
    @Test
    void shouldRefuseAKeyThatMatchesNoBuildAgent() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PublicKey strangerKey = generator.generateKeyPair().getPublic();

        assertThat(createService(List.of()).authenticate(AGENT_NAME, strangerKey, sessionFrom(AGENT_ADDRESS))).isFalse();
    }

    /**
     * Without local CI there is no registry to consult. The agent is then unconstrained here, and the repository check
     * refuses it instead: a node with no build jobs can scope nothing, so it serves nothing.
     */
    @Test
    void shouldNotConstrainTheOriginWithoutARegistry() {
        var userSshPublicKeyRepository = mock(UserSshPublicKeyRepository.class);
        when(userSshPublicKeyRepository.findByKeyHash(any())).thenReturn(Optional.empty());
        var service = new GitPublickeyAuthenticatorService(mock(UserRepository.class), Optional.of(distributedDataAccessService), userSshPublicKeyRepository,
                mock(RateLimitService.class), new BuildAgentNetworkPolicy(new BuildAgentNetworkConfiguration(), new MockEnvironment()), Optional.empty());

        assertThat(service.authenticate(AGENT_NAME, agentPublicKey, sessionFrom("203.0.113.9"))).isTrue();
    }
}
