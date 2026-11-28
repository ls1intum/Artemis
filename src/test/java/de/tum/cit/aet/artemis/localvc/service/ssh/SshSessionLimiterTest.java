package de.tum.cit.aet.artemis.localvc.service.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Map;

import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.session.SessionListener.Event;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void sessionThatClosesWhileItIsAdmittedDoesNotKeepItsSlot() throws Exception {
        SshSessionLimiter limiter = new SshSessionLimiter(1);
        ServerSession closing = sessionFrom("203.0.113.5");
        // open when the admission starts, closed by the time the session is registered
        when(closing.isClosing()).thenReturn(false, true);
        limiter.sessionEvent(closing, Event.KeyEstablished);

        ServerSession next = sessionFrom("203.0.113.5");
        limiter.sessionEvent(next, Event.KeyEstablished);

        verify(next, never()).disconnect(anyInt(), anyString());
    }

    @Test
    void sessionThatIsAlreadyClosedIsNotCounted() throws Exception {
        SshSessionLimiter limiter = new SshSessionLimiter(1);
        ServerSession closed = sessionFrom("203.0.113.5");
        when(closed.isClosing()).thenReturn(true);
        limiter.sessionEvent(closed, Event.KeyEstablished);

        ServerSession next = sessionFrom("203.0.113.5");
        limiter.sessionEvent(next, Event.KeyEstablished);

        verify(next, never()).disconnect(anyInt(), anyString());
    }

    @Test
    void aRefusedSessionThatIsAlreadyClosingLeavesNothingBehind() throws Exception {
        SshSessionLimiter limiter = new SshSessionLimiter(1);
        limiter.sessionEvent(sessionFrom("203.0.113.5"), Event.KeyEstablished);
        ServerSession refused = sessionFrom("203.0.113.5");
        // open when the admission starts, closing by the time the refusal is recorded
        when(refused.isClosing()).thenReturn(false, true);

        limiter.sessionEvent(refused, Event.KeyEstablished);

        verify(refused).disconnect(SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS, "Too many open connections");
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(limiter, "decided")).as("only the admitted session is recorded").hasSize(1);
    }
}
