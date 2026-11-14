package de.tum.cit.aet.artemis.account.security.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.SessionFreshnessService;
import de.tum.cit.aet.artemis.notification.service.GlobalNotificationSettingService;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * A passkey is a permanent credential, so a session that survived a password reset must not be able to register one.
 * <p>
 * The registration runs in servlet filters, outside the controllers that otherwise ask {@link SessionFreshnessService}, so the
 * filters are tested on their own: both the step that creates the options, which fails early, and the step that stores the passkey.
 */
class PasskeyRegistrationSessionGuardTest {

    private static final String LOGIN = "student1";

    private SessionFreshnessService sessionFreshnessService;

    @BeforeEach
    void setUp() {
        sessionFreshnessService = mock(SessionFreshnessService.class);
        var authentication = new UsernamePasswordAuthenticationToken(LOGIN, "token", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(Map.of(TokenProvider.SESSION_STARTED_AT, Instant.parse("2026-10-13T10:00:00Z")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ArtemisWebAuthnRegistrationFilter registrationFilter() {
        return new ArtemisWebAuthnRegistrationFilter(mock(UserCredentialRepository.class), mock(WebAuthnRelyingPartyOperations.class), mock(MailSendingService.class),
                mock(UserRepository.class), mock(GlobalNotificationSettingService.class), sessionFreshnessService);
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    @Test
    void theRegistrationOfAPasskeyIsRefusedForAnOutdatedSession() throws Exception {
        when(sessionFreshnessService.isCurrentSession(SecurityContextHolder.getContext().getAuthentication())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        registrationFilter().doFilter(post("/webauthn/register"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("sessionOutdated");
        // Neither Spring's registration nor anything behind it ran, so nothing was stored.
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void theRegistrationOptionsAreRefusedForAnOutdatedSession() throws Exception {
        when(sessionFreshnessService.isCurrentSession(SecurityContextHolder.getContext().getAuthentication())).thenReturn(false);
        var filter = new ArtemisPublicKeyCredentialCreationOptionsFilter(mock(WebAuthnRelyingPartyOperations.class), sessionFreshnessService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/webauthn/register/options"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("sessionOutdated");
    }

    /**
     * The guard must not touch the other requests the filter sees, or it would refuse every request of an outdated session
     * rather than only the creation of a credential.
     */
    @Test
    void otherRequestsPassThroughTheRegistrationFilterUnchecked() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        registrationFilter().doFilter(post("/api/some/other/endpoint"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
