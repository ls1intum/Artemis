package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;
import inet.ipaddr.IPAddress;

/**
 * Which client address the request limit of git operations charges: the peer of the connection, or the client named by the forwarding header of a configured proxy.
 */
class LocalVCServletServiceClientAddressTest {

    private static final String PROXY = "198.51.100.7";

    private static final String CLIENT = "203.0.113.9";

    private RateLimitService rateLimitService;

    private LocalVCServletService serviceWithTrustedProxies(String... trustedProxies) {
        rateLimitService = mock(RateLimitService.class);
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setTrustedProxies(List.of(trustedProxies));
        return new LocalVCServletService(mock(AuthenticationManager.class), mock(UserRepository.class), mock(ProgrammingExerciseRepository.class),
                mock(ProgrammingExerciseBuildConfigRepository.class), mock(RepositoryAccessService.class), mock(ProgrammingExerciseParticipationService.class),
                mock(AuxiliaryRepositoryService.class), Optional.empty(), mock(ParticipationVCSAccessTokenRepository.class), mock(RepositoryVCSAccessTokenRepository.class),
                mock(AuthorizationCheckService.class), rateLimitService, mock(UserVcsAccessTokenService.class), mock(LocalVCRepositoryResolverService.class),
                mock(LocalVCAccessLogRecorderService.class), mock(LocalVCBuildAgentAuthenticationService.class), mock(PasswordAuthenticationNoticeService.class),
                new BuildAgentNetworkPolicy(configuration, new MockEnvironment()), 20L, 50L);
    }

    /**
     * Sends the handshake request of a clone through the service and returns the address that was charged. The request stops after the charge, because the repository
     * resolver is a mock that resolves nothing.
     */
    private String chargedAddress(LocalVCServletService service, String peer, String forwardedFor) {
        var request = new MockHttpServletRequest("GET", "/git/PROJECT/project-student1.git/info/refs");
        request.setRemoteAddr(peer);
        request.addHeader("Authorization", "Basic c3R1ZGVudDE6cGFzc3dvcmQ=");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        assertThatThrownBy(() -> service.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ)).isInstanceOf(NullPointerException.class);
        ArgumentCaptor<IPAddress> address = ArgumentCaptor.forClass(IPAddress.class);
        verify(rateLimitService).enforcePerMinute(address.capture(), eq(RateLimitType.GIT_AUTHENTICATION));
        return address.getValue().toString();
    }

    @Test
    void chargesThePeerAndIgnoresTheHeaderOfAnUntrustedPeer() {
        var service = serviceWithTrustedProxies();

        assertThat(chargedAddress(service, PROXY, CLIENT)).isEqualTo(PROXY);
    }

    @Test
    void chargesThePeerWhenTheRequestHasNoForwardingHeader() {
        var service = serviceWithTrustedProxies(PROXY);

        assertThat(chargedAddress(service, PROXY, null)).isEqualTo(PROXY);
    }

    @Test
    void chargesTheClientNamedByTheHeaderOfATrustedProxy() {
        var service = serviceWithTrustedProxies(PROXY);

        assertThat(chargedAddress(service, PROXY, CLIENT)).isEqualTo(CLIENT);
    }

    @Test
    void chargesTheEntryTheProxyAppendedWhenTheClientSentAHeaderOfItsOwn() {
        var service = serviceWithTrustedProxies(PROXY);

        assertThat(chargedAddress(service, PROXY, "192.0.2.77, " + CLIENT)).isEqualTo(CLIENT);
    }

    @Test
    void ignoresTheHeaderOfAPeerThatIsNotTheTrustedProxy() {
        var service = serviceWithTrustedProxies(PROXY);

        assertThat(chargedAddress(service, "198.51.100.99", CLIENT)).isEqualTo("198.51.100.99");
    }

    @Test
    void chargesNothingForTheFirstRequestWithoutCredentials() {
        var service = serviceWithTrustedProxies(PROXY);
        var request = new MockHttpServletRequest("GET", "/git/PROJECT/project-student1.git/info/refs");
        request.setRemoteAddr(PROXY);

        assertThatThrownBy(() -> service.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ)).isInstanceOf(LocalVCAuthException.class);
        verifyNoInteractions(rateLimitService);
    }
}
