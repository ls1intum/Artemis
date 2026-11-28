package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.exception.SessionOutdatedException;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Unit tests for the check that keeps a session which survived a credential change from creating new credentials.
 * <p>
 * The failure mode is silent in both directions: a check that is never applied leaves the permanent-credential hole of a stale
 * session open, and one that is applied to a request that carries no session start would lock out every internal call. Both
 * are therefore pinned here, next to the decision itself.
 */
@ExtendWith(MockitoExtension.class)
class SessionFreshnessServiceTest {

    private static final String LOGIN = "student1";

    @Mock
    private PasskeyTokenRenewalService passkeyTokenRenewalService;

    private SessionFreshnessService service() {
        return new SessionFreshnessService(passkeyTokenRenewalService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication authenticationWithSessionStartedAt(Instant startedAt) {
        var authentication = new UsernamePasswordAuthenticationToken(LOGIN, "token", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(Map.of(TokenProvider.SESSION_STARTED_AT, startedAt));
        return authentication;
    }

    @Test
    void aSessionThatStartedAfterTheLastCredentialsChangeMayCreateCredentials() {
        Instant startedAt = Instant.parse("2026-10-13T10:00:00Z");
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(LOGIN, startedAt)).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(authenticationWithSessionStartedAt(startedAt));

        assertThatCode(() -> service().requireCurrentSession()).doesNotThrowAnyException();
    }

    /**
     * The session that was in use during an intrusion has to stop being able to plant a passkey, an SSH key or an access token,
     * which would each outlive the password reset that was meant to end the intrusion.
     */
    @Test
    void aSessionThatStartedBeforeTheLastCredentialsChangeIsRefusedWith401() {
        Instant startedAt = Instant.parse("2026-10-13T10:00:00Z");
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(LOGIN, startedAt)).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authenticationWithSessionStartedAt(startedAt));

        assertThatThrownBy(() -> service().requireCurrentSession()).isInstanceOf(SessionOutdatedException.class)
                .satisfies(exception -> assertThat(((SessionOutdatedException) exception).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void theErrorTellsTheClientWhichKeyToTranslate() {
        SessionOutdatedException exception = new SessionOutdatedException();

        assertThat(exception.getErrorKey()).isEqualTo("sessionOutdated");
        assertThat(exception.getBody().getProperties()).containsEntry("message", "error.sessionOutdated");
    }

    /**
     * A request that does not come from a signed token, such as an internal call or a test with a mock user, has no session
     * that could predate the change, so nothing is looked up.
     */
    @Test
    void anAuthenticationWithoutASessionStartIsNotChecked() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(LOGIN, "token", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThatCode(() -> service().requireCurrentSession()).doesNotThrowAnyException();
        verifyNoInteractions(passkeyTokenRenewalService);
    }

    @Test
    void aRequestWithoutAnAuthenticationIsNotChecked() {
        assertThat(service().isCurrentSession(null)).isTrue();
        verifyNoInteractions(passkeyTokenRenewalService);
    }
}
