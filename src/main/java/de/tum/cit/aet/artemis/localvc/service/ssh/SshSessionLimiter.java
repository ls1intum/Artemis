package de.tum.cit.aet.artemis.localvc.service.ssh;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.localvc.service.AuthenticationContext;

/**
 * Bounds the number of SSH sessions that one client address keeps open at the same time. A session over the limit is disconnected right after the key exchange, before it can
 * authenticate or use a thread for a git command. The address is the client address that the proxy handling of the SSH server reports, so it is the real client behind a load
 * balancer.
 */
public class SshSessionLimiter implements SessionListener {

    private static final Logger log = LoggerFactory.getLogger(SshSessionLimiter.class);

    private final int maxSessionsPerAddress;

    private final ConcurrentMap<String, Integer> sessionsPerAddress = new ConcurrentHashMap<>();

    private final ConcurrentMap<Session, String> countedSessions = new ConcurrentHashMap<>();

    private final ConcurrentMap<Session, AtomicBoolean> decided = new ConcurrentHashMap<>();

    public SshSessionLimiter(int maxSessionsPerAddress) {
        this.maxSessionsPerAddress = maxSessionsPerAddress;
    }

    @Override
    public void sessionEvent(Session session, Event event) {
        if (event != Event.KeyEstablished || decided.computeIfAbsent(session, _ -> new AtomicBoolean()).getAndSet(true)) {
            return;
        }
        if (!(session instanceof ServerSession serverSession)) {
            return;
        }
        String address = AuthenticationContext.hostAddressOf(serverSession.getClientAddress());
        if (address == null) {
            return;
        }
        int count = sessionsPerAddress.merge(address, 1, Integer::sum);
        if (count > maxSessionsPerAddress) {
            release(address);
            log.warn("Refusing an SSH session from {}: {} sessions are already open", address, maxSessionsPerAddress);
            try {
                session.disconnect(SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS, "Too many open connections");
            }
            catch (Exception e) {
                log.debug("Could not disconnect the SSH session from {}", address, e);
            }
            return;
        }
        countedSessions.put(session, address);
    }

    @Override
    public void sessionClosed(Session session) {
        decided.remove(session);
        String address = countedSessions.remove(session);
        if (address != null) {
            release(address);
        }
    }

    private void release(String address) {
        sessionsPerAddress.computeIfPresent(address, (_, count) -> count <= 1 ? null : count - 1);
    }
}
