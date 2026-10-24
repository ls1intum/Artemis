package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import de.tum.cit.aet.artemis.account.domain.User;
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

    private static User account(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    /**
     * The read of the account has loaded the account already, so deciding must not look it up by login a second time. That lookup is paid on every load of the app by
     * everyone who has a personal access token.
     */
    @Test
    void anAccountThatIsAlreadyLoadedIsUsedInsteadOfBeingLookedUpAgain() {
        Instant startedAt = Instant.parse("2026-10-13T10:00:00Z");
        User account = account(LOGIN);
        when(passkeyTokenRenewalService.mayExtendSessionOf(account, startedAt)).thenReturn(true, false);
        Authentication authentication = authenticationWithSessionStartedAt(startedAt);

        assertThat(service().isCurrentSession(authentication, account)).isTrue();
        assertThat(service().isCurrentSession(authentication, account)).isFalse();
        verify(passkeyTokenRenewalService, never()).mayExtendSessionForAccount(anyString(), any(Instant.class));
    }

    /**
     * Passing another account must never make a session look current: the decision is then made for the account that the session names.
     */
    @Test
    void anAccountThatIsNotTheOneOfTheSessionIsNotTrusted() {
        Instant startedAt = Instant.parse("2026-10-13T10:00:00Z");
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(LOGIN, startedAt)).thenReturn(false);

        assertThat(service().isCurrentSession(authenticationWithSessionStartedAt(startedAt), account("someone-else"))).isFalse();
        verify(passkeyTokenRenewalService, never()).mayExtendSessionOf(any(User.class), any(Instant.class));
    }

    @Test
    void aSessionWithoutAStartIsNotCheckedAgainstTheLoadedAccount() {
        Authentication withoutSessionStart = new UsernamePasswordAuthenticationToken(LOGIN, "token", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(service().isCurrentSession(withoutSessionStart, account(LOGIN))).isTrue();
        assertThat(service().isCurrentSession(null, account(LOGIN))).isTrue();
        verifyNoInteractions(passkeyTokenRenewalService);
    }
}
