package de.tum.cit.aet.artemis.programming.service.localvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;

class AuthenticationContextTest extends AbstractProgrammingIntegrationIndependentTest {

    @Test
    void testSessionContext_getIpAddress() {
        ServerSession session = mock(ServerSession.class);
        InetSocketAddress clientAddress = InetSocketAddress.createUnresolved("192.168.1.10", 22);
        when(session.getClientAddress()).thenReturn(clientAddress);

        AuthenticationContext.Session sessionContext = new AuthenticationContext.Session(session);

        String ipAddress = sessionContext.getIpAddress();

        assertThat(ipAddress).contains("192.168.1.10");
        assertThat(ipAddress).contains("22");
    }

    @Test
    void testSessionContext_getIpAddress_withResolved() {
        ServerSession session = mock(ServerSession.class);
        InetSocketAddress clientAddress = new InetSocketAddress("10.0.0.50", 2222);
        when(session.getClientAddress()).thenReturn(clientAddress);

        AuthenticationContext.Session sessionContext = new AuthenticationContext.Session(session);

        String ipAddress = sessionContext.getIpAddress();

        assertThat(ipAddress).contains("10.0.0.50");
    }

    @Test
    void testRequestContext_getIpAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.16.0.100");

        AuthenticationContext.Request requestContext = new AuthenticationContext.Request(request);

        String ipAddress = requestContext.getIpAddress();

        assertThat(ipAddress).isEqualTo("172.16.0.100");
    }

    @Test
    void testRequestContext_getIpAddress_withNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(null);

        AuthenticationContext.Request requestContext = new AuthenticationContext.Request(request);

        String ipAddress = requestContext.getIpAddress();

        assertThat(ipAddress).isNull();
    }

    @Test
    void testAuthenticationContext_sealedInterface() {
        // Test that we can create instances of both record types
        ServerSession session = mock(ServerSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        AuthenticationContext sessionContext = new AuthenticationContext.Session(session);
        AuthenticationContext requestContext = new AuthenticationContext.Request(request);

        assertThat(sessionContext).isInstanceOf(AuthenticationContext.Session.class);
        assertThat(requestContext).isInstanceOf(AuthenticationContext.Request.class);
    }

    @Test
    void testSessionRecord_equality() {
        ServerSession session1 = mock(ServerSession.class);
        ServerSession session2 = mock(ServerSession.class);

        AuthenticationContext.Session context1 = new AuthenticationContext.Session(session1);
        AuthenticationContext.Session context1Copy = new AuthenticationContext.Session(session1);
        AuthenticationContext.Session context2 = new AuthenticationContext.Session(session2);

        assertThat(context1).isEqualTo(context1Copy);
        assertThat(context1).isNotEqualTo(context2);
        assertThat(context1.hashCode()).isEqualTo(context1Copy.hashCode());
    }

    @Test
    void testRequestRecord_equality() {
        HttpServletRequest request1 = mock(HttpServletRequest.class);
        HttpServletRequest request2 = mock(HttpServletRequest.class);

        AuthenticationContext.Request context1 = new AuthenticationContext.Request(request1);
        AuthenticationContext.Request context1Copy = new AuthenticationContext.Request(request1);
        AuthenticationContext.Request context2 = new AuthenticationContext.Request(request2);

        assertThat(context1).isEqualTo(context1Copy);
        assertThat(context1).isNotEqualTo(context2);
        assertThat(context1.hashCode()).isEqualTo(context1Copy.hashCode());
    }
}
