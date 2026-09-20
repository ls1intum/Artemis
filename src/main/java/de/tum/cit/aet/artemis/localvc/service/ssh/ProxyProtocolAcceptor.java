package de.tum.cit.aet.artemis.localvc.service.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import jakarta.annotation.PostConstruct;

import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.session.AbstractServerSession;
import org.apache.sshd.server.session.ServerProxyAcceptor;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.SshProxyProtocolConfiguration;
import de.tum.cit.aet.artemis.core.util.IpRangeSet;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Recovers the real client address of an SSH connection that reached this node through a TCP load balancer, by reading
 * the HAProxy PROXY protocol header the balancer prepends.
 * <p>
 * Without this, every connection forwarded by an nginx {@code stream} block appears to come from the balancer:
 * rate limiting collapses into one bucket shared by all users, the access log records the balancer, and an origin
 * check on a build agent cannot distinguish one agent from another. {@link AbstractServerSession#setClientAddress}
 * feeds the parsed address back into {@link ServerSession#getClientAddress()}, which the git paths already read.
 * <p>
 * Whether a header is required is decided by the connection's source address, never by whether a header happens to be
 * present. Trusting a header from any sender would let anyone who can reach the SSH port claim an arbitrary client
 * address, which is precisely what an origin check must not permit. So a connection from a configured proxy must begin
 * with a valid header and is closed otherwise, and a connection from anywhere else is passed through untouched.
 * <p>
 * Both PROXY protocol versions are handled: the human-readable v1 line and the binary v2 header. {@code LOCAL}
 * commands and unsupported address families are accepted but leave the client address alone, as the specification
 * requires - those are the balancer's own health checks, not forwarded client traffic.
 *
 * @see SshProxyProtocolConfiguration
 */
@Component
@Profile(PROFILE_LOCALVC)
@Lazy(false)
@EnableConfigurationProperties(SshProxyProtocolConfiguration.class)
public class ProxyProtocolAcceptor implements ServerProxyAcceptor {

    private static final Logger log = LoggerFactory.getLogger(ProxyProtocolAcceptor.class);

    private static final String TRUSTED_SOURCES_PROPERTY = "artemis.version-control.ssh-proxy-protocol.trusted-sources";

    /**
     * The 12 byte prefix that opens a v2 header. Chosen by the specification so that it cannot be confused with any
     * valid v1 line or with an SSH identification string.
     */
    private static final byte[] V2_SIGNATURE = { 0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A };

    private static final byte[] V1_PREFIX = "PROXY ".getBytes(StandardCharsets.US_ASCII);

    /** A v1 line is at most 107 bytes including the terminating CRLF. */
    private static final int V1_MAX_LENGTH = 107;

    /** Signature, version/command byte, family/protocol byte and the 2 byte length field. */
    private static final int V2_HEADER_LENGTH = 16;

    /**
     * Largest address block this accepts: the 36 bytes of an IPv6 address pair plus room for the specification's type
     * length value extensions. The field itself allows up to 65535, which is only ever a malformed or hostile header.
     */
    private static final int V2_MAX_ADDRESS_LENGTH = 536;

    private static final int V2_VERSION_2 = 0x20;

    private static final int V2_COMMAND_PROXY = 0x01;

    private static final int V2_FAMILY_INET = 0x10;

    private static final int V2_FAMILY_INET6 = 0x20;

    private final IpRangeSet trustedSources;

    public ProxyProtocolAcceptor(SshProxyProtocolConfiguration configuration) {
        this.trustedSources = IpRangeSet.parse(configuration.getTrustedSources(), TRUSTED_SOURCES_PROPERTY);
    }

    /**
     * States at startup whether ssh connections will be attributed to the real client or to the load balancer, since
     * the difference is otherwise only visible much later, in whichever address ends up in the access log.
     */
    @PostConstruct
    public void logConfiguredSources() {
        if (trustedSources.isEmpty()) {
            log.info("PROXY protocol is disabled for the git ssh server ({} is empty). If ssh reaches this node through a load balancer, every connection will be attributed to "
                    + "the balancer rather than to the client.", TRUSTED_SOURCES_PROPERTY);
        }
        else {
            log.info("The git ssh server expects a PROXY protocol header from {}. Those sources must have proxy_protocol enabled.", trustedSources);
        }
    }

    /**
     * @return whether any trusted source is configured, i.e. whether this acceptor does anything
     */
    public boolean isEnabled() {
        return !trustedSources.isEmpty();
    }

    @Override
    public boolean acceptServerProxyMetadata(ServerSession session, Buffer buffer) throws Exception {
        if (trustedSources.isEmpty()) {
            return true;
        }

        // The raw socket peer, deliberately not getClientAddress(): this method is called again for the same session
        // when the ssh identification line that follows the header arrives incomplete, and by then getClientAddress()
        // would already return the address we parsed out of the header.
        String peer = hostOf(session.getIoSession().getRemoteAddress());
        if (!trustedSources.contains(peer)) {
            // Not a load balancer we operate, so no header is expected and none may be believed. Leave the read
            // position untouched so the ssh identification line is read from the start of the data.
            return true;
        }

        byte[] data = buffer.array();
        int start = buffer.rpos();
        int length = buffer.wpos() - start;

        if (startsWith(data, start, length, V2_SIGNATURE)) {
            return parseVersion2(session, buffer, data, start, length);
        }
        if (startsWith(data, start, length, V1_PREFIX)) {
            return parseVersion1(session, buffer, data, start, length);
        }

        // Not (yet) recognisable. While the received bytes are still a prefix of either marker, more data may complete
        // it; once they diverge, this is not a PROXY header at all and the connection must not be served, because it
        // came from an address whose traffic we would otherwise attribute to a client it never named.
        if (isPrefixOf(data, start, length, V2_SIGNATURE) || isPrefixOf(data, start, length, V1_PREFIX)) {
            return false;
        }
        throw new IllegalStateException("Expected a PROXY protocol header from " + peer + ", which is configured in " + TRUSTED_SOURCES_PROPERTY
                + ", but the connection did not start with one. Enable proxy_protocol for this listener on that proxy, or remove the address from that property.");
    }

    /**
     * Parses the human-readable v1 line, {@code PROXY TCP4 <src> <dst> <srcPort> <dstPort>\r\n}.
     */
    private boolean parseVersion1(ServerSession session, Buffer buffer, byte[] data, int start, int length) throws UnknownHostException {
        // Bounded to the maximum permitted line length: without this, a malformed line with no terminator of its own
        // would find the CRLF of the ssh identification line that follows and parse the two as one header.
        int lineEnd = indexOfCrLf(data, start, Math.min(length, V1_MAX_LENGTH));
        if (lineEnd < 0) {
            if (length >= V1_MAX_LENGTH) {
                throw new IllegalStateException("Received a PROXY protocol v1 header longer than the permitted " + V1_MAX_LENGTH + " bytes without a line terminator");
            }
            return false;
        }

        String line = new String(data, start, lineEnd - start, StandardCharsets.US_ASCII);
        // +2 for the CRLF: the ssh identification line begins immediately after it
        buffer.rpos(lineEnd + 2);

        String[] parts = line.split(" ");
        // "PROXY UNKNOWN" carries no usable address, which the specification allows; keep the socket peer.
        if (parts.length < 6 || "UNKNOWN".equals(parts[1])) {
            log.debug("PROXY protocol v1 header without a usable client address: {}", line);
            return true;
        }

        applyClientAddress(session, parts[2], parts[4]);
        return true;
    }

    /**
     * Parses the binary v2 header.
     */
    private boolean parseVersion2(ServerSession session, Buffer buffer, byte[] data, int start, int length) throws UnknownHostException {
        if (length < V2_HEADER_LENGTH) {
            return false;
        }

        int versionAndCommand = data[start + 12] & 0xFF;
        int familyAndProtocol = data[start + 13] & 0xFF;
        int addressLength = ((data[start + 14] & 0xFF) << 8) | (data[start + 15] & 0xFF);
        int totalLength = V2_HEADER_LENGTH + addressLength;

        // Both checks precede the wait for more data below. A header that declares a length we will never accept, or a
        // version we do not speak, has to fail now: waiting first would stall the connection until the idle timeout
        // collects it, because the announced bytes are never going to arrive.
        if ((versionAndCommand & 0xF0) != V2_VERSION_2) {
            throw new IllegalStateException("Unsupported PROXY protocol version in header byte 0x" + Integer.toHexString(versionAndCommand));
        }
        if (addressLength > V2_MAX_ADDRESS_LENGTH) {
            throw new IllegalStateException("PROXY protocol v2 header declares " + addressLength + " address bytes, more than the " + V2_MAX_ADDRESS_LENGTH + " this accepts");
        }

        if (length < totalLength) {
            return false;
        }

        buffer.rpos(start + totalLength);

        // LOCAL means the proxy is speaking for itself, typically a health check, and carries no client address.
        if ((versionAndCommand & 0x0F) != V2_COMMAND_PROXY) {
            log.debug("PROXY protocol v2 LOCAL command, keeping the socket peer as the client address");
            return true;
        }

        int family = familyAndProtocol & 0xF0;
        int addressStart = start + V2_HEADER_LENGTH;
        if (family == V2_FAMILY_INET && addressLength >= 12) {
            applyClientAddress(session, Arrays.copyOfRange(data, addressStart, addressStart + 4), readPort(data, addressStart + 8));
        }
        else if (family == V2_FAMILY_INET6 && addressLength >= 36) {
            applyClientAddress(session, Arrays.copyOfRange(data, addressStart, addressStart + 16), readPort(data, addressStart + 32));
        }
        else {
            // AF_UNSPEC or AF_UNIX: nothing an IP based check could use, so keep the socket peer.
            log.debug("PROXY protocol v2 header with address family 0x{}, keeping the socket peer as the client address", Integer.toHexString(family));
        }
        return true;
    }

    private static int readPort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private void applyClientAddress(ServerSession session, String host, String port) throws UnknownHostException {
        int parsedPort;
        try {
            parsedPort = Integer.parseInt(port);
        }
        catch (NumberFormatException e) {
            throw new IllegalStateException("Cannot parse '" + port + "' as the client port of a PROXY protocol header", e);
        }

        // Parsed strictly as a numeric literal, never resolved. InetAddress.getByName would perform a DNS lookup for
        // anything that is not one, which would block this ssh handshake on a name server at the request of whoever
        // wrote the header. The v1 specification requires a numeric address here, so a non-numeric value is malformed.
        IPAddress parsedAddress = new IPAddressString(host).getAddress();
        if (parsedAddress == null) {
            throw new IllegalStateException("Cannot parse '" + host + "' as the client address of a PROXY protocol header");
        }
        applyClientAddress(session, parsedAddress.toInetAddress(), parsedPort);
    }

    private void applyClientAddress(ServerSession session, byte[] rawAddress, int port) throws UnknownHostException {
        applyClientAddress(session, InetAddress.getByAddress(rawAddress), port);
    }

    private void applyClientAddress(ServerSession session, InetAddress address, int port) {
        if (session instanceof AbstractServerSession serverSession) {
            // Idempotent: this method runs again for the same session whenever the ssh identification line that
            // follows the header arrives in pieces, and it then sets the same address a second time.
            //
            // The static analyser reports SSRF here because header-derived data reaches an InetSocketAddress. Nothing
            // in this class opens a connection: the address is recorded as session metadata, and is only ever read
            // afterwards as a rate limiting key, an access log field and an allowlist comparison. There is no request
            // for an attacker to redirect. It is also not a name resolution sink - the address arrives either as raw
            // bytes from a v2 header or parsed strictly as a literal from a v1 line, never resolved.
            // nosemgrep
            serverSession.setClientAddress(new InetSocketAddress(address, port));
            log.debug("Resolved the ssh client behind the proxy as {}:{}", address.getHostAddress(), port);
        }
        else {
            log.warn("Cannot record the client address of an ssh session of type {}", session.getClass().getName());
        }
    }

    private static String hostOf(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int start, int length, byte[] marker) {
        if (length < marker.length) {
            return false;
        }
        return Arrays.equals(data, start, start + marker.length, marker, 0, marker.length);
    }

    /**
     * @return whether the received bytes are a strict prefix of the marker, i.e. whether more data could still turn
     *         them into it
     */
    private static boolean isPrefixOf(byte[] data, int start, int length, byte[] marker) {
        if (length >= marker.length) {
            return false;
        }
        return Arrays.equals(data, start, start + length, marker, 0, length);
    }

    private static int indexOfCrLf(byte[] data, int start, int length) {
        for (int index = start; index < start + length - 1; index++) {
            if (data[index] == '\r' && data[index + 1] == '\n') {
                return index;
            }
        }
        return -1;
    }
}
