package de.tum.cit.aet.artemis.localvc.service;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.jspecify.annotations.Nullable;

public sealed interface AuthenticationContext {

    record Session(ServerSession session) implements AuthenticationContext {

        @Override
        public String getIpAddress() {
            return hostAddressOf(session.getClientAddress());
        }
    }

    record Request(HttpServletRequest request) implements AuthenticationContext {

        @Override
        public String getIpAddress() {
            return request.getRemoteAddr();
        }
    }

    String getIpAddress();

    /**
     * Extracts the plain address of a socket peer, for the access log and for rate limiting.
     * <p>
     * Deliberately not {@link SocketAddress#toString()}: that prints {@code hostname/192.0.2.1:52134}, which is not an
     * address at all and, once a reverse lookup supplies a long hostname, does not fit the {@code vcs_access_log.ip_address}
     * column either, so the whole audit entry is lost to a failed insert. The numeric form is also what the allowlist and
     * the rate limiter compare against, so every reader of a peer address wants the same string.
     *
     * @param address the peer address, already the real client where the proxy protocol is in use
     * @return the numeric address, the host string where the address was never resolved, or null if it is not an ip socket
     */
    @Nullable
    static String hostAddressOf(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress inetSocketAddress)) {
            return null;
        }
        // An unresolved address carries no InetAddress; its host string is the literal the address was created from.
        if (inetSocketAddress.getAddress() == null) {
            return inetSocketAddress.getHostString();
        }
        return inetSocketAddress.getAddress().getHostAddress();
    }
}
