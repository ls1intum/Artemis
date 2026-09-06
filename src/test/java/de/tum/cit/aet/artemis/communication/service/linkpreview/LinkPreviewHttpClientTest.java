package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LinkPreviewHttpClientTest {

    private static final InetAddress LOOPBACK = InetAddress.ofLiteral("127.0.0.1");

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(3);

    @Test
    void sendsOriginFormRequestAndReadsFixedLengthResponse() throws Exception {
        byte[] responseBytes = response("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nX-Test: first\r\nX-Test: second\r\n\r\nhello");
        try (TestServer server = TestServer.http(responseBytes)) {
            URI uri = URI.create("http://example.com:" + server.port() + "/some%20path?value=%C3%A4");

            LinkPreviewHttpClient.Response response = get(uri, List.of(LOOPBACK), 10);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).asString(StandardCharsets.US_ASCII).isEqualTo("hello");
            assertThat(response.firstHeader("X-TEST")).isEqualTo("first");
            assertThat(response.firstHeader("missing")).isNull();
            assertThat(server.request()).startsWith("GET /some%20path?value=%C3%A4 HTTP/1.1\r\n").contains("Host: example.com:" + server.port() + "\r\n")
                    .contains("Connection: close\r\n");
            assertThatThrownBy(() -> response.headers().put("new", List.of("value"))).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void skipsInformationalResponseAndReadsChunkedBody() throws Exception {
        byte[] responseBytes = response(
                "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4;extension=value\r\nWiki\r\n5\r\npedia\r\n0\r\nTrailer: value\r\n\r\n");
        try (TestServer server = TestServer.http(responseBytes)) {
            LinkPreviewHttpClient.Response response = get(server.uri("http", "/"), List.of(LOOPBACK), 20);

            assertThat(response.body()).asString(StandardCharsets.US_ASCII).isEqualTo("Wikipedia");
        }
    }

    @Test
    void readsBodyUntilConnectionCloses() throws Exception {
        try (TestServer server = TestServer.http(response("HTTP/1.0 200 OK\r\n\r\nbody"))) {
            LinkPreviewHttpClient.Response response = get(server.uri("http", "/"), List.of(LOOPBACK), 4);

            assertThat(response.body()).asString(StandardCharsets.US_ASCII).isEqualTo("body");
        }
    }

    @Test
    void doesNotReadBodiesThatCannotContainPreviewContent() throws Exception {
        try (TestServer noContentServer = TestServer.http(response("HTTP/1.1 204 No Content\r\nContent-Length: 100\r\n\r\n"));
                TestServer redirectServer = TestServer.http(response("HTTP/1.1 302 Found\r\nContent-Length: 100\r\nLocation: /target\r\n\r\n"))) {
            LinkPreviewHttpClient.Response noContent = get(noContentServer.uri("http", "/"), List.of(LOOPBACK), 1);
            LinkPreviewHttpClient.Response redirect = get(redirectServer.uri("http", "/"), List.of(LOOPBACK), 1);

            assertThat(noContent.body()).isEmpty();
            assertThat(redirect.body()).isEmpty();
            assertThat(redirect.firstHeader("location")).isEqualTo("/target");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void rejectsMalformedOrOversizedResponse(String description, String rawResponse, int maximumResponseSize, String expectedMessage) throws Exception {
        try (TestServer server = TestServer.http(response(rawResponse))) {
            assertThatThrownBy(() -> get(server.uri("http", "/"), List.of(LOOPBACK), maximumResponseSize)).isInstanceOf(IOException.class).hasMessageContaining(expectedMessage);
        }
    }

    @Test
    void triesValidatedAddressesInOrder() throws Exception {
        InetAddress unavailableLoopbackAddress = InetAddress.ofLiteral("::1");
        try (TestServer server = TestServer.http(response("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"))) {
            LinkPreviewHttpClient.Response response = get(server.uri("http", "/"), List.of(unavailableLoopbackAddress, LOOPBACK), 2);

            assertThat(response.body()).asString(StandardCharsets.US_ASCII).isEqualTo("ok");
        }
    }

    @Test
    void rejectsEmptyAddressListAndExpiredDeadline() throws Exception {
        URI uri = URI.create("http://example.com:80/");
        var emptyValidatedUrl = new LinkPreviewUrlValidator.ValidatedUrl(uri, List.of());

        assertThatThrownBy(() -> LinkPreviewHttpClient.get(emptyValidatedUrl, new LinkPreviewHttpClient.RequestDeadline(TEST_TIMEOUT), 10)).isInstanceOf(IOException.class)
                .hasMessage("The link preview host did not resolve to an address");

        var expiredDeadline = new LinkPreviewHttpClient.RequestDeadline(Duration.ofMillis(1));
        Thread.sleep(10);
        var validatedUrl = new LinkPreviewUrlValidator.ValidatedUrl(uri, List.of(LOOPBACK));
        assertThatThrownBy(() -> LinkPreviewHttpClient.get(validatedUrl, expiredDeadline, 10)).isInstanceOf(IOException.class).hasMessageContaining("timed out");
    }

    @Test
    void rejectsNonPositiveDeadline() {
        assertThatThrownBy(() -> new LinkPreviewHttpClient.RequestDeadline(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkPreviewHttpClient.RequestDeadline(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifiesTlsCertificateHostnameWhileConnectingToPinnedAddress() throws Exception {
        TlsContexts tlsContexts = createTlsContexts("other.example.com");
        try (TestServer server = TestServer.https(tlsContexts.serverContext(), response("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"))) {
            URI uri = server.uri("https", "/");
            var validatedUrl = new LinkPreviewUrlValidator.ValidatedUrl(uri, List.of(LOOPBACK));

            assertThatThrownBy(() -> LinkPreviewHttpClient.get(validatedUrl, new LinkPreviewHttpClient.RequestDeadline(TEST_TIMEOUT), 2, tlsContexts.clientSocketFactory()))
                    .isInstanceOf(SSLHandshakeException.class);
        }
    }

    @Test
    void retrievesTlsResponseUsingOriginalHostnameAndPinnedAddress() throws Exception {
        TlsContexts tlsContexts = createTlsContexts("example.com");
        try (TestServer server = TestServer.https(tlsContexts.serverContext(), response("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"))) {
            URI uri = server.uri("https", "/");
            var validatedUrl = new LinkPreviewUrlValidator.ValidatedUrl(uri, List.of(LOOPBACK));

            LinkPreviewHttpClient.Response response = LinkPreviewHttpClient.get(validatedUrl, new LinkPreviewHttpClient.RequestDeadline(TEST_TIMEOUT), 2,
                    tlsContexts.clientSocketFactory());

            assertThat(response.body()).asString(StandardCharsets.US_ASCII).isEqualTo("ok");
            assertThat(server.request()).contains("Host: example.com:" + server.port() + "\r\n");
        }
    }

    private static Stream<Arguments> invalidResponses() {
        return Stream.of(Arguments.of("missing status line", "", 10, "status line"), Arguments.of("invalid status line", "invalid\r\n\r\n", 10, "invalid status line"),
                Arguments.of("non-numeric status", "HTTP/1.1 nope\r\n\r\n", 10, "invalid status code"),
                Arguments.of("out-of-range status", "HTTP/1.1 42 Invalid\r\n\r\n", 10, "invalid status code"),
                Arguments.of("truncated headers", "HTTP/1.1 200 OK\r\nHeader: value", 10, "headers ended unexpectedly"),
                Arguments.of("invalid header", "HTTP/1.1 200 OK\r\nInvalid\r\n\r\n", 10, "invalid header"),
                Arguments.of("oversized header line", "HTTP/1.1 200 OK\r\nX: " + "a".repeat(65 * 1024) + "\r\n\r\n", 10, "oversized header line"),
                Arguments.of("oversized headers", "HTTP/1.1 200 OK\r\n" + "X: " + "a".repeat(40 * 1024) + "\r\n" + "Y: " + "b".repeat(40 * 1024) + "\r\n\r\n", 10,
                        "headers exceeded"),
                Arguments.of("unsupported transfer encoding", "HTTP/1.1 200 OK\r\nTransfer-Encoding: gzip\r\n\r\n", 10, "unsupported transfer encoding"),
                Arguments.of("multiple transfer encodings", "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\n", 10,
                        "unsupported transfer encoding"),
                Arguments.of("invalid content length", "HTTP/1.1 200 OK\r\nContent-Length: invalid\r\n\r\n", 10, "invalid content length"),
                Arguments.of("negative content length", "HTTP/1.1 200 OK\r\nContent-Length: -1\r\n\r\n", 10, "invalid content length"),
                Arguments.of("conflicting content lengths", "HTTP/1.1 200 OK\r\nContent-Length: 1, 2\r\n\r\n", 10, "invalid content length"),
                Arguments.of("fixed body too large", "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\n123456", 5, "exceeded the maximum size"),
                Arguments.of("truncated fixed body", "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\n123", 10, "body ended unexpectedly"),
                Arguments.of("close-delimited body too large", "HTTP/1.1 200 OK\r\n\r\n123456", 5, "exceeded the maximum size"),
                Arguments.of("missing chunk header", "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", 10, "body ended unexpectedly"),
                Arguments.of("invalid chunk size", "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\ninvalid\r\n", 10, "invalid chunk size"),
                Arguments.of("chunk too large", "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n6\r\n123456\r\n0\r\n\r\n", 5, "exceeded the maximum size"),
                Arguments.of("invalid chunk framing", "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1\r\naXX", 10, "invalid chunk framing"),
                Arguments.of("missing chunk trailers", "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n", 10, "trailers ended unexpectedly"),
                Arguments.of("oversized chunk trailers",
                        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX: " + "a".repeat(40 * 1024) + "\r\nY: " + "b".repeat(40 * 1024) + "\r\n\r\n", 10,
                        "trailers exceeded"));
    }

    private static LinkPreviewHttpClient.Response get(URI uri, List<InetAddress> addresses, int maximumResponseSize) throws IOException {
        var validatedUrl = new LinkPreviewUrlValidator.ValidatedUrl(uri, addresses);
        return LinkPreviewHttpClient.get(validatedUrl, new LinkPreviewHttpClient.RequestDeadline(TEST_TIMEOUT), maximumResponseSize);
    }

    private static byte[] response(String response) {
        return response.getBytes(StandardCharsets.US_ASCII);
    }

    private static TlsContexts createTlsContexts(String certificateHost) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=" + certificateHost);
        var certificateBuilder = new JcaX509v3CertificateBuilder(subject, new BigInteger(128, new SecureRandom()), Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)), subject, keyPair.getPublic());
        certificateBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.dNSName, certificateHost)));
        X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(certificateBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

        char[] password = "link-preview-test".toCharArray();
        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(null, null);
        serverKeyStore.setKeyEntry("server", keyPair.getPrivate(), password, new Certificate[] { certificate });
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(serverKeyStore, password);
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagerFactory.getKeyManagers(), null, null);

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", certificate);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return new TlsContexts(serverContext, clientContext.getSocketFactory());
    }

    private record TlsContexts(SSLContext serverContext, SSLSocketFactory clientSocketFactory) {
    }

    private static final class TestServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        private final AtomicReference<Socket> connection = new AtomicReference<>();

        private final Future<String> request;

        private TestServer(ServerSocket serverSocket, byte[] responseBytes) {
            this.serverSocket = serverSocket;
            request = executor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    connection.set(socket);
                    socket.setSoTimeout((int) TEST_TIMEOUT.toMillis());
                    if (socket instanceof SSLSocket sslSocket) {
                        sslSocket.startHandshake();
                    }
                    String requestValue = readRequest(socket.getInputStream());
                    socket.getOutputStream().write(responseBytes);
                    socket.getOutputStream().flush();
                    return requestValue;
                }
            });
        }

        private static TestServer http(byte[] responseBytes) throws IOException {
            return new TestServer(new ServerSocket(0, 50, LOOPBACK), responseBytes);
        }

        private static TestServer https(SSLContext sslContext, byte[] responseBytes) throws IOException {
            SSLServerSocket serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(0, 50, LOOPBACK);
            serverSocket.setEnabledProtocols(new String[] { "TLSv1.3" });
            return new TestServer(serverSocket, responseBytes);
        }

        private URI uri(String scheme, String path) {
            return URI.create(scheme + "://example.com:" + port() + path);
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private String request() throws Exception {
            return request.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            Socket acceptedSocket = connection.get();
            if (acceptedSocket != null) {
                acceptedSocket.close();
            }
            executor.shutdownNow();
            executor.awaitTermination(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        private static String readRequest(InputStream inputStream) throws IOException {
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            int matchedTerminatorBytes = 0;
            while (request.size() < 64 * 1024) {
                int value = inputStream.read();
                if (value < 0) {
                    break;
                }
                request.write(value);
                int expected = switch (matchedTerminatorBytes) {
                    case 0, 2 -> '\r';
                    case 1, 3 -> '\n';
                    default -> throw new IllegalStateException("Unexpected request terminator state");
                };
                if (value == expected) {
                    matchedTerminatorBytes++;
                    if (matchedTerminatorBytes == 4) {
                        return request.toString(StandardCharsets.US_ASCII);
                    }
                }
                else {
                    matchedTerminatorBytes = value == '\r' ? 1 : 0;
                }
            }
            throw new IOException("The test request headers ended unexpectedly");
        }
    }
}
