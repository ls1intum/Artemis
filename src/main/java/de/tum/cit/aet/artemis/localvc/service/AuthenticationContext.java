package de.tum.cit.aet.artemis.localvc.service;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.util.IpAddresses;

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
     * Deliberately not {@link SocketAddress#toString()}, which prints {@code hostname/192.0.2.1:52134}: that is not an
     * address, and once a reverse lookup supplies a long hostname it no longer fits the {@code vcs_access_log.ip_address}
     * column, losing the audit entry to a failed insert. A hostname is rejected wherever else it appears for the same
     * reason, and because it is not what the allowlist and the rate limiter compare against.
     *
     * @param address the peer address, already the real client where the proxy protocol is in use
     * @return the numeric address, or null if the peer is not an ip socket or is named by a hostname
     */
    @Nullable
    static String hostAddressOf(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress inetSocketAddress)) {
            return null;
        }
        if (inetSocketAddress.getAddress() != null) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        // An unresolved address was never looked up, so its host string is an address only if it was created from one.
        String hostString = inetSocketAddress.getHostString();
        return IpAddresses.canonical(hostString) != null ? hostString : null;
    }
}
