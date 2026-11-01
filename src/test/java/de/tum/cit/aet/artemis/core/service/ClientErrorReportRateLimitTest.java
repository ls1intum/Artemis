package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.domain.ClientErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ClientErrorReportDTO;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.Role;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Tests that only a client that is not authenticated is limited, and that a client that exceeds its limit is not logged.
 */
class ClientErrorReportRateLimitTest {

    private static final String USER_AGENT = "Mozilla/5.0";

    private static final ClientErrorReportDTO REPORT = new ClientErrorReportDTO(ClientErrorSeverity.ERROR, "boom", null, "/login", "9.8.0", null, null);

    private final IPAddress client = new IPAddressString("203.0.113.7").getAddress();

    private RateLimitService rateLimitService;

    private ClientErrorReportService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        rateLimitService = mock(RateLimitService.class);
        when(rateLimitService.resolveClientId()).thenReturn(client);
        service = spy(new ClientErrorReportService(rateLimitService));
        doNothing().when(service).logReport(any(), any(), anyBoolean());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsAClientThatIsNotAuthenticatedBeforeItLogs() {
        service.report(REPORT, USER_AGENT);

        InOrder inOrder = inOrder(rateLimitService, service);
        inOrder.verify(rateLimitService).enforcePerMinute(client, RateLimitType.CLIENT_ERROR_REPORT);
        inOrder.verify(service).logReport(REPORT, USER_AGENT, false);
    }

    @Test
    void limitsAnAnonymousSessionLikeAClientWithoutAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()))));

        service.report(REPORT, USER_AGENT);

        verify(rateLimitService).enforcePerMinute(client, RateLimitType.CLIENT_ERROR_REPORT);
        verify(service).logReport(REPORT, USER_AGENT, false);
    }

    @Test
    void doesNotLogWhenAClientThatIsNotAuthenticatedExceedsItsLimit() {
        doThrow(new RateLimitExceededException(12)).when(rateLimitService).enforcePerMinute(any(), eq(RateLimitType.CLIENT_ERROR_REPORT));

        assertThatThrownBy(() -> service.report(REPORT, USER_AGENT)).isInstanceOf(RateLimitExceededException.class).extracting("retryAfterSeconds").isEqualTo(12L);

        verify(service, never()).logReport(any(), any(), anyBoolean());
    }

    @Test
    void doesNotLimitAnAuthenticatedClient() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("student", "n/a", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()))));

        service.report(REPORT, USER_AGENT);

        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        verify(rateLimitService, never()).enforcePerMinute(any(), any());
        verify(service).logReport(REPORT, USER_AGENT, true);
    }
}
