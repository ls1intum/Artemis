package de.tum.cit.aet.artemis.localvc.service.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.session.AbstractServerSession;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.core.config.SshProxyProtocolConfiguration;

/**
 * Tests for {@link ProxyProtocolAcceptor}.
 * <p>
 * The security-relevant property is not that the parser works but that it is keyed on the source address: a PROXY
 * header must be required from a configured proxy and never believed from anyone else. Trusting a header because it is
 * present would let anybody who can reach the ssh port name whatever client address an origin check permits.
 * <p>
 * The other subtle requirement is re-entrancy. MINA SSHD rewinds the buffer and calls the acceptor again whenever the
 * ssh identification line that follows the header arrives incomplete, so parsing has to work repeatedly on the same
 * bytes rather than assume it runs once.
 */
class ProxyProtocolAcceptorTest {

    private static final byte[] V2_SIGNATURE = { 0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A };

    private static final int V2_HEADER_LENGTH = 16;

    private static final int V2_FAMILY_INET_STREAM = 0x11;

    private static final int V2_FAMILY_INET6_STREAM = 0x21;

    private static final String SSH_IDENTIFICATION = "SSH-2.0-OpenSSH_9.0\r\n";

    private static ProxyProtocolAcceptor acceptorTrusting(String... trustedSources) {
        SshProxyProtocolConfiguration configuration = new SshProxyProtocolConfiguration();
        configuration.setTrustedSources(List.of(trustedSources));
        return new ProxyProtocolAcceptor(configuration);
    }

    private static AbstractServerSession sessionFrom(String peerAddress) {
        AbstractServerSession session = mock(AbstractServerSession.class);
        IoSession ioSession = mock(IoSession.class);
        when(session.getIoSession()).thenReturn(ioSession);
        when(ioSession.getRemoteAddress()).thenReturn(new InetSocketAddress(peerAddress, 51234));
        return session;
    }

    private static Buffer bufferOf(byte[] content) {
        return new ByteArrayBuffer(content);
    }

    private static byte[] concat(byte[] first, byte[] second) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(first);
        out.write(second);
        return out.toByteArray();
    }

    private static InetSocketAddress capturedClientAddress(AbstractServerSession session) {
        ArgumentCaptor<java.net.SocketAddress> captor = ArgumentCaptor.forClass(java.net.SocketAddress.class);
        verify(session).setClientAddress(captor.capture());
        return (InetSocketAddress) captor.getValue();
    }

    @Test
    void shouldDoNothingWhenNoTrustedSourceIsConfigured() {
        ProxyProtocolAcceptor acceptor = acceptorTrusting();
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(SSH_IDENTIFICATION.getBytes(StandardCharsets.US_ASCII));

        assertThatCode(() -> assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue()).doesNotThrowAnyException();
        assertThat(acceptor.isEnabled()).isFalse();
        verify(session, never()).setClientAddress(any());
    }

    /**
     * The core rule. A direct connection is ordinary ssh even though it carries something that looks like a header, so
     * nobody outside the configured proxies can choose the address they are judged by.
     */
    @Test
    void shouldIgnoreAHeaderFromAnUntrustedPeer() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.1");
        AbstractServerSession session = sessionFrom("203.0.113.9");
        Buffer buffer = bufferOf(("PROXY TCP4 1.2.3.4 5.6.7.8 1111 2222\r\n" + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));
        int readPositionBefore = buffer.rpos();

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        verify(session, never()).setClientAddress(any());
        assertThat(buffer.rpos()).as("nothing may be consumed, the bytes are part of the ssh stream as far as this connection is concerned").isEqualTo(readPositionBefore);
    }

    @Test
    void shouldParseAVersion1Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        String header = "PROXY TCP4 198.51.100.7 10.0.0.5 56324 7921\r\n";
        Buffer buffer = bufferOf((header + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        InetSocketAddress clientAddress = capturedClientAddress(session);
        assertThat(clientAddress.getAddress().getHostAddress()).isEqualTo("198.51.100.7");
        assertThat(clientAddress.getPort()).isEqualTo(56324);
        assertThat(buffer.rpos()).as("the read position must point at the ssh identification line").isEqualTo(header.length());
    }

    @Test
    void shouldParseAVersion1Ipv6Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(("PROXY TCP6 2001:db8::1 2001:db8::2 56324 7921\r\n" + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        assertThat(capturedClientAddress(session).getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    /**
     * A balancer health check announces itself with UNKNOWN, which carries no client address. Keeping the socket peer
     * is what the specification requires, and it must not be mistaken for a parse failure.
     */
    @Test
    void shouldKeepTheSocketPeerForAnUnknownVersion1Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        String header = "PROXY UNKNOWN\r\n";
        Buffer buffer = bufferOf((header + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        verify(session, never()).setClientAddress(any());
        assertThat(buffer.rpos()).isEqualTo(header.length());
    }

    @Test
    void shouldParseAVersion2Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] header = version2Ipv4Header(new byte[] { (byte) 198, 51, 100, 7 }, 56324);
        Buffer buffer = bufferOf(concat(header, SSH_IDENTIFICATION.getBytes(StandardCharsets.US_ASCII)));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        InetSocketAddress clientAddress = capturedClientAddress(session);
        assertThat(clientAddress.getAddress().getHostAddress()).isEqualTo("198.51.100.7");
        assertThat(clientAddress.getPort()).isEqualTo(56324);
        assertThat(buffer.rpos()).isEqualTo(header.length);
    }

    /**
     * LOCAL means the proxy speaks for itself, again typically a health check.
     */
    @Test
    void shouldKeepTheSocketPeerForAVersion2LocalCommand() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] header = version2Ipv4Header(new byte[] { (byte) 198, 51, 100, 7 }, 56324);
        header[12] = 0x20; // version 2, LOCAL
        Buffer buffer = bufferOf(concat(header, SSH_IDENTIFICATION.getBytes(StandardCharsets.US_ASCII)));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        verify(session, never()).setClientAddress(any());
    }

    @Test
    void shouldWaitForMoreDataOnAnIncompleteVersion1Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf("PROXY TCP4 198.51.100.7 10.0.0.5 563".getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).as("without a line terminator the header may still be completed by the next packet").isFalse();
        verify(session, never()).setClientAddress(any());
    }

    @Test
    void shouldWaitForMoreDataOnAnIncompleteVersion2Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(V2_SIGNATURE);

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isFalse();
        verify(session, never()).setClientAddress(any());
    }

    @Test
    void shouldWaitWhileTheReceivedBytesAreStillAPrefixOfAMarker() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf("PRO".getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isFalse();
    }

    /**
     * The connection came from an address configured as a proxy but did not speak the protocol. Serving it would mean
     * attributing traffic to a client it never named, so the session must be closed instead.
     */
    @Test
    void shouldRejectATrustedSourceThatSendsNoHeader() {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(SSH_IDENTIFICATION.getBytes(StandardCharsets.US_ASCII));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptor.acceptServerProxyMetadata(session, buffer)).withMessageContaining("PROXY protocol")
                .withMessageContaining("10.0.0.1");
    }

    /**
     * The v1 specification requires a numeric address, and it must be parsed as one rather than resolved: resolving
     * would let whoever wrote the header block this ssh handshake on a name server of their choosing.
     */
    @Test
    void shouldRejectANonNumericClientAddressWithoutResolvingIt() {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(("PROXY TCP4 attacker-controlled.example.com 10.0.0.5 56324 7921\r\n" + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptor.acceptServerProxyMetadata(session, buffer))
                .withMessageContaining("attacker-controlled.example.com");
        verify(session, never()).setClientAddress(any());
    }

    @Test
    void shouldFailStartupOnAMalformedTrustedSource() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptorTrusting("not-an-address"))
                .withMessageContaining("artemis.version-control.ssh-proxy-protocol.trusted-sources");
    }

    /**
     * MINA SSHD rewinds the buffer and calls again when the identification line that follows arrives incomplete, so
     * the same bytes are parsed more than once for one connection. Re-parsing must produce the same result rather than
     * throw or consume the wrong number of bytes.
     */
    @Test
    void shouldBeRepeatableForTheSameConnection() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        String header = "PROXY TCP4 198.51.100.7 10.0.0.5 56324 7921\r\n";
        byte[] content = (header + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII);

        Buffer firstAttempt = bufferOf(content);
        assertThat(acceptor.acceptServerProxyMetadata(session, firstAttempt)).isTrue();

        Buffer secondAttempt = bufferOf(content);
        assertThat(acceptor.acceptServerProxyMetadata(session, secondAttempt)).isTrue();
        assertThat(secondAttempt.rpos()).isEqualTo(header.length());
    }

    /**
     * A client reaching the balancer over IPv6 is the case a v4-only parser silently drops: the header parses, no
     * address is applied, and every such session is attributed to the balancer with nothing in the log to say so.
     */
    @Test
    void shouldParseAVersion2Ipv6Header() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] sourceAddress = new byte[16];
        sourceAddress[0] = 0x20;
        sourceAddress[1] = 0x01;
        sourceAddress[2] = 0x0D;
        sourceAddress[3] = (byte) 0xB8;
        sourceAddress[15] = 0x07;
        byte[] header = version2Header(V2_FAMILY_INET6_STREAM, sourceAddress, new byte[16], 56324);
        Buffer buffer = bufferOf(concat(header, SSH_IDENTIFICATION.getBytes(StandardCharsets.US_ASCII)));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        InetSocketAddress clientAddress = capturedClientAddress(session);
        assertThat(clientAddress.getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:7");
        assertThat(clientAddress.getPort()).isEqualTo(56324);
    }

    /**
     * AF_UNSPEC is what a balancer sends for a connection it cannot describe in IP terms. There is no address to apply,
     * and treating that as a failure would close a connection the specification considers valid.
     */
    @Test
    void shouldKeepTheSocketPeerForAVersion2UnspecifiedFamily() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] header = version2Header(0x00, new byte[4], new byte[4], 56324);
        Buffer buffer = bufferOf(concat(header, SSH_IDENTIFICATION.getBytes(StandardCharsets.US_ASCII)));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        verify(session, never()).setClientAddress(any());
        assertThat(buffer.rpos()).isEqualTo(header.length);
    }

    /**
     * Both of the next two are the reason the version and length checks sit ahead of the wait for more data. Returning
     * false here instead would hold the connection open until the idle timeout collected it, because the bytes the
     * header announces are never going to arrive - a cheap way to accumulate sessions on the ssh listener.
     */
    @Test
    void shouldRejectAnUnsupportedVersion2HeaderVersionImmediately() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] header = version2Header(V2_FAMILY_INET_STREAM, new byte[] { (byte) 198, 51, 100, 7 }, new byte[4], 56324);
        header[12] = 0x31; // version 3

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptor.acceptServerProxyMetadata(session, bufferOf(header)))
                .withMessageContaining("Unsupported PROXY protocol version");
        verify(session, never()).setClientAddress(any());
    }

    @Test
    void shouldRejectAVersion2HeaderDeclaringAnOversizedAddressBlockImmediately() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] header = version2Header(V2_FAMILY_INET_STREAM, new byte[] { (byte) 198, 51, 100, 7 }, new byte[4], 56324);
        header[14] = 0x10; // 4096 announced address bytes
        header[15] = 0x00;

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptor.acceptServerProxyMetadata(session, bufferOf(header))).withMessageContaining("4096")
                .withMessageContaining("536");
        verify(session, never()).setClientAddress(any());
    }

    /**
     * The legitimate counterpart of the two above: a header whose declared length is acceptable but whose address bytes
     * have not all arrived yet is a normal partial read, so it has to wait rather than fail.
     */
    @Test
    void shouldWaitForTheAnnouncedVersion2AddressBytes() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        byte[] header = version2Header(V2_FAMILY_INET_STREAM, new byte[] { (byte) 198, 51, 100, 7 }, new byte[4], 56324);
        Buffer buffer = bufferOf(Arrays.copyOfRange(header, 0, V2_HEADER_LENGTH + 4));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isFalse();
        verify(session, never()).setClientAddress(any());
    }

    /**
     * The v1 line is bounded by the specification. Without the bound a line with no terminator of its own would run on
     * into the ssh identification line that follows and the two would be parsed as one header.
     */
    @Test
    void shouldRejectAVersion1HeaderWithNoTerminatorWithinTheMaximumLength() {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(("PROXY TCP4 198.51.100.7 10.0.0.5 " + "9".repeat(120)).getBytes(StandardCharsets.US_ASCII));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptor.acceptServerProxyMetadata(session, buffer)).withMessageContaining("107");
        verify(session, never()).setClientAddress(any());
    }

    @Test
    void shouldRejectAnUnparsableClientPort() {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = sessionFrom("10.0.0.1");
        Buffer buffer = bufferOf(("PROXY TCP4 198.51.100.7 10.0.0.5 not-a-port 7921\r\n" + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> acceptor.acceptServerProxyMetadata(session, buffer)).withMessageContaining("not-a-port");
        verify(session, never()).setClientAddress(any());
    }

    /**
     * A peer that is not an IP socket at all - a unix domain socket, or a mock in a test - has no address to compare
     * against the trusted sources. That is an ordinary "not a proxy", not an error: throwing here would turn an
     * unexpected transport into a failed ssh handshake.
     */
    @Test
    void shouldTreatANonIpPeerAsUntrusted() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        AbstractServerSession session = mock(AbstractServerSession.class);
        IoSession ioSession = mock(IoSession.class);
        when(session.getIoSession()).thenReturn(ioSession);
        when(ioSession.getRemoteAddress()).thenReturn(new SocketAddress() {
        });
        Buffer buffer = bufferOf(("PROXY TCP4 1.2.3.4 5.6.7.8 1111 2222\r\n" + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();

        verify(session, never()).setClientAddress(any());
    }

    /**
     * {@code setClientAddress} is declared on {@link AbstractServerSession}, not on the {@link ServerSession} interface
     * this method receives. An implementation that is not one has to be logged rather than cast, or a valid header
     * would end in a ClassCastException out of the handshake.
     */
    @Test
    void shouldNotFailOnASessionThatCannotRecordAClientAddress() throws Exception {
        ProxyProtocolAcceptor acceptor = acceptorTrusting("10.0.0.0/8");
        ServerSession session = mock(ServerSession.class);
        IoSession ioSession = mock(IoSession.class);
        when(session.getIoSession()).thenReturn(ioSession);
        when(ioSession.getRemoteAddress()).thenReturn(new InetSocketAddress("10.0.0.1", 51234));
        String header = "PROXY TCP4 198.51.100.7 10.0.0.5 56324 7921\r\n";
        Buffer buffer = bufferOf((header + SSH_IDENTIFICATION).getBytes(StandardCharsets.US_ASCII));

        assertThat(acceptor.acceptServerProxyMetadata(session, buffer)).isTrue();
        assertThat(buffer.rpos()).isEqualTo(header.length());
    }

    /**
     * The startup log is the only place an operator can see that the property they set in the balancer and the property
     * they set in Artemis do not agree, so it has to say which of the two states this node is in.
     */
    @Test
    void shouldLogWhetherProxyProtocolIsActive() {
        assertThatCode(() -> {
            acceptorTrusting().logConfiguredSources();
            acceptorTrusting("10.0.0.0/8").logConfiguredSources();
        }).doesNotThrowAnyException();
        assertThat(acceptorTrusting().isEnabled()).isFalse();
        assertThat(acceptorTrusting("10.0.0.0/8").isEnabled()).isTrue();
    }

    private static byte[] version2Header(int familyAndProtocol, byte[] sourceAddress, byte[] destinationAddress, int sourcePort) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(V2_SIGNATURE);
        out.write(0x21); // version 2, PROXY
        out.write(familyAndProtocol);
        int addressLength = sourceAddress.length + destinationAddress.length + 4;
        out.write((addressLength >> 8) & 0xFF);
        out.write(addressLength & 0xFF);
        out.write(sourceAddress);
        out.write(destinationAddress);
        out.write((sourcePort >> 8) & 0xFF);
        out.write(sourcePort & 0xFF);
        out.write(0x1E); // destination port 7921
        out.write(0xF1);
        return out.toByteArray();
    }

    private static byte[] version2Ipv4Header(byte[] sourceAddress, int sourcePort) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(V2_SIGNATURE);
        out.write(0x21); // version 2, PROXY
        out.write(0x11); // AF_INET, STREAM
        out.write(0x00); // address block length, high byte
        out.write(12); // address block length, low byte
        out.write(sourceAddress);
        out.write(new byte[] { 10, 0, 0, 5 }); // destination address
        out.write((sourcePort >> 8) & 0xFF);
        out.write(sourcePort & 0xFF);
        out.write(0x1E); // destination port 7921
        out.write(0xF1);
        return out.toByteArray();
    }
}
