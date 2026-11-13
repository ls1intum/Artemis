package de.tum.cit.aet.artemis.localvc.service.ssh;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.session.SessionListener.Event;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.Test;

class SshSessionLimiterTest {

    private static ServerSession sessionFrom(String address) {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(new InetSocketAddress(address, 40000));
        return session;
    }

    @Test
    void sessionOverTheLimitIsDisconnected() throws Exception {
        SshSessionLimiter limiter = new SshSessionLimiter(2);
        ServerSession first = sessionFrom("203.0.113.5");
        ServerSession second = sessionFrom("203.0.113.5");
        ServerSession third = sessionFrom("203.0.113.5");
        limiter.sessionEvent(first, Event.KeyEstablished);
        limiter.sessionEvent(second, Event.KeyEstablished);
        limiter.sessionEvent(third, Event.KeyEstablished);

        verify(first, never()).disconnect(anyInt(), anyString());
        verify(second, never()).disconnect(anyInt(), anyString());
        verify(third).disconnect(SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS, "Too many open connections");
    }

    @Test
    void closedSessionFreesItsSlotAndOtherAddressesAreIndependent() throws Exception {
        SshSessionLimiter limiter = new SshSessionLimiter(1);
        ServerSession first = sessionFrom("203.0.113.5");
        limiter.sessionEvent(first, Event.KeyEstablished);
        ServerSession other = sessionFrom("203.0.113.6");
        limiter.sessionEvent(other, Event.KeyEstablished);
        limiter.sessionClosed(first);
        ServerSession replacement = sessionFrom("203.0.113.5");
        limiter.sessionEvent(replacement, Event.KeyEstablished);

        verify(other, never()).disconnect(anyInt(), anyString());
        verify(replacement, never()).disconnect(anyInt(), anyString());
    }

    @Test
    void repeatedKeyExchangeOfOneSessionIsCountedOnce() throws Exception {
        SshSessionLimiter limiter = new SshSessionLimiter(1);
        ServerSession first = sessionFrom("203.0.113.5");
        limiter.sessionEvent(first, Event.KeyEstablished);
        limiter.sessionEvent(first, Event.KeyEstablished);

        verify(first, never()).disconnect(anyInt(), anyString());
    }
}
