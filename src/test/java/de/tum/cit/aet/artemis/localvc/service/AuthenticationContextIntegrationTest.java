package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;

class AuthenticationContextIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    @Test
    void testSessionContext_getIpAddress_unresolved() {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(InetSocketAddress.createUnresolved("192.168.1.10", 22));

        String ipAddress = new AuthenticationContext.Session(session).getIpAddress();

        assertThat(ipAddress).isEqualTo("192.168.1.10");
    }

    @Test
    void testSessionContext_getIpAddress_withResolved() {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(new InetSocketAddress("10.0.0.50", 2222));

        String ipAddress = new AuthenticationContext.Session(session).getIpAddress();

        assertThat(ipAddress).isEqualTo("10.0.0.50");
    }

    @Test
    void testSessionContext_getIpAddress_withHostname() throws UnknownHostException {
        // A peer whose reverse lookup supplied a hostname, as production ssh clients arrive
        InetSocketAddress clientAddress = new InetSocketAddress(InetAddress.getByAddress("host-203-0-113-42.dialup.example.net", new byte[] { (byte) 203, 0, (byte) 113, 42 }),
                52134);
        assertThat(clientAddress.toString()).hasSizeGreaterThan(45);

        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(clientAddress);

        String ipAddress = new AuthenticationContext.Session(session).getIpAddress();

        assertThat(ipAddress).isEqualTo("203.0.113.42");
    }

    @Test
    void testSessionContext_getIpAddress_ipv6() throws UnknownHostException {
        InetSocketAddress clientAddress = new InetSocketAddress(InetAddress.getByName("2001:db8::1"), 22);

        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(clientAddress);

        String ipAddress = new AuthenticationContext.Session(session).getIpAddress();

        assertThat(ipAddress).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void testSessionContext_getIpAddress_unresolvedHostname() {
        // An unresolved address keeps whatever string it was created from, so a hostname arrives here unshortened
        String hostname = "a-very-long-student-machine-name.subdomain.students.example.net";
        assertThat(hostname).hasSizeGreaterThan(45);

        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(InetSocketAddress.createUnresolved(hostname, 22));

        String ipAddress = new AuthenticationContext.Session(session).getIpAddress();

        assertThat(ipAddress).isNull();
    }

    @Test
    void testSessionContext_getIpAddress_nonIpSocket() {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(new SocketAddress() {
        });

        String ipAddress = new AuthenticationContext.Session(session).getIpAddress();

        assertThat(ipAddress).isNull();
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
