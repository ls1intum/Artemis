package de.tum.cit.aet.artemis.core.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.net.ssl.SSLHandshakeException;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class LoopbackAgentApiServiceTest {

    private static final String TOKEN = "Bearer art_pat_context_test";

    private static final String MARKDOWN = "# Grüße: λ 中文 — mathematical explanations";

    @TempDir
    private Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(strings = { "", "/", "/artemis" })
    void callsTheTlsConnectorWithTheConfiguredSelfSignedCertificate(String contextPath) throws Exception {
        var ssl = developmentSsl();
        var factory = new TomcatServletWebServerFactory(0);
        factory.setSsl(ssl);
        factory.setContextPath(contextPath.equals("/") ? "" : contextPath);
        String expectedPath = (contextPath.equals("/") ? "" : contextPath) + "/api/core/context-test";
        var server = factory.getWebServer(servletContext -> servletContext.addServlet("api", new HttpServlet() {

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                boolean valid = TOKEN.equals(request.getHeader(HttpHeaders.AUTHORIZATION)) && expectedPath.equals(request.getRequestURI());
                if (request.getMethod().equals("PATCH")) {
                    valid &= MediaType.parseMediaType(request.getContentType()).isCompatibleWith(MediaType.TEXT_PLAIN)
                            && MARKDOWN.equals(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
                if (request.getMethod().equals("POST")) {
                    valid &= MediaType.parseMediaType(request.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON)
                            && ("{\"text\":\"" + MARKDOWN + "\"}").equals(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
                response.setStatus(valid ? 200 : 400);
                response.getWriter().write("{\"ok\":true}");
            }
        }).addMapping("/*"));
        server.start();
        var properties = new ServerProperties();
        properties.setSsl(ssl);
        try (var context = clientContext(server.getPort(), properties)) {
            bindRequest(contextPath);
            var api = context.getBean(LoopbackAgentApiService.class);

            assertThat(api.send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(200);
            assertThat(api.send(HttpMethod.POST, "/api/core/context-test", new TextPayload(MARKDOWN)).status()).isEqualTo(200);
            assertThat(api.sendText(HttpMethod.PATCH, "/api/core/context-test", MARKDOWN).status()).isEqualTo(200);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop();
            server.destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "pem", "bundle", "bundle-uppercase-alias" })
    void supportsPemAndNamedBundleCertificatesForThePublicHostname(String configuration) throws Exception {
        KeyStore keyStore = serverKeyStore(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        var bundle = configuration.equals("bundle-uppercase-alias") ? SslBundle.of(SslStoreBundle.of(keyStore, "password", null), SslBundleKey.of("password", "SERVER"))
                : sslBundle(keyStore);
        var registry = new DefaultSslBundleRegistry();
        var ssl = new Ssl();
        if (!configuration.equals("pem")) {
            registry.registerBundle("server", bundle);
            ssl.setBundle("server");
        }
        else {
            Path certificate = temporaryDirectory.resolve("server.crt");
            Path privateKey = temporaryDirectory.resolve("server.key");
            FileUtils.writeStringToFile(certificate.toFile(), pem("CERTIFICATE", keyStore.getCertificate("server").getEncoded()), StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(privateKey.toFile(), pem("PRIVATE KEY", keyStore.getKey("server", "password".toCharArray()).getEncoded()), StandardCharsets.UTF_8);
            ssl.setCertificate(certificate.toUri().toString());
            ssl.setCertificatePrivateKey(privateKey.toUri().toString());
        }
        var properties = new ServerProperties();
        properties.setSsl(ssl);
        var server = httpsServer(sslBundle(keyStore));
        var received = new AtomicInteger();
        server.createContext("/api/core/context-test", exchange -> {
            if (TOKEN.equals(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
                received.incrementAndGet();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try (var context = clientContext(server.getAddress().getPort(), properties, registry)) {
            bindRequest("");

            assertThat(context.getBean(LoopbackAgentApiService.class).send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(204);
            assertThat(received).hasValue(1);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop(0);
        }
    }

    @Test
    void reloadsNamedBundlePinsWhenTheServerCertificateRotates() throws Exception {
        Instant now = Instant.now();
        var original = sslBundle(serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600)));
        var replacement = sslBundle(serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600)));
        var unrelated = sslBundle(serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600)));
        var serverBundles = new DefaultSslBundleRegistry("server", original);
        var clientBundles = new DefaultSslBundleRegistry("server", original);
        var received = new AtomicInteger();
        var server = rotatingTlsServer(serverBundles, received);
        var properties = new ServerProperties();
        properties.setSsl(Ssl.forBundle("server"));
        try (var context = clientContext(server.getPort(), properties, clientBundles)) {
            bindRequest("");
            var api = context.getBean(LoopbackAgentApiService.class);
            assertThat(api.send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(204);
            assertThat(received).hasValue(1);

            serverBundles.updateBundle("server", replacement);
            clientBundles.updateBundle("server", replacement);

            assertThat(api.send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(204);
            assertThat(received).hasValue(2);

            // Only the connector rolls back: the client must keep trusting exclusively the replacement certificate.
            serverBundles.updateBundle("server", original);
            assertThatThrownBy(() -> api.send(HttpMethod.GET, "/api/core/context-test", null)).hasCauseInstanceOf(SSLHandshakeException.class);
            assertThat(received).hasValue(2);

            serverBundles.updateBundle("server", unrelated);
            assertThatThrownBy(() -> api.send(HttpMethod.GET, "/api/core/context-test", null)).hasCauseInstanceOf(SSLHandshakeException.class);
            assertThat(received).hasValue(2);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop();
            server.destroy();
        }
    }

    @Test
    void doesNotResumeASessionAuthenticatedByTheRetiredCertificate() throws Exception {
        Instant now = Instant.now();
        var originalStore = serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600));
        var original = SslBundle.of(SslStoreBundle.of(originalStore, "password", null), SslBundleKey.of("password", "server"), SslOptions.of(null, new String[] { "TLSv1.2" }));
        var replacement = sslBundle(serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600)));
        var serverBundles = new DefaultSslBundleRegistry("server", original);
        var clientBundles = new DefaultSslBundleRegistry("server", original);
        var received = new AtomicInteger();
        var server = rotatingTlsServer(serverBundles, received);
        var properties = new ServerProperties();
        properties.setSsl(Ssl.forBundle("server"));
        try (var context = clientContext(server.getPort(), properties, clientBundles)) {
            bindRequest("");
            var api = context.getBean(LoopbackAgentApiService.class);
            assertThat(api.send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(204);
            assertThat(received).hasValue(1);

            // Keep the original server SSLContext and its cached sessions. Rotating only the client's trust must retire them.
            clientBundles.updateBundle("server", replacement);

            assertThatThrownBy(() -> api.send(HttpMethod.GET, "/api/core/context-test", null)).hasCauseInstanceOf(SSLHandshakeException.class);
            assertThat(received).hasValue(1);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop();
            server.destroy();
        }
    }

    @Test
    void bundleUpdatesDoNotRestartTheClientAfterContextShutdown() throws Exception {
        Instant now = Instant.now();
        var original = sslBundle(serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600)));
        var replacement = sslBundle(serverKeyStore(now.minusSeconds(60), now.plusSeconds(3600)));
        var serverBundles = new DefaultSslBundleRegistry("server", original);
        var clientBundles = new DefaultSslBundleRegistry("server", original);
        var received = new AtomicInteger();
        var server = rotatingTlsServer(serverBundles, received);
        var properties = new ServerProperties();
        properties.setSsl(Ssl.forBundle("server"));
        var context = clientContext(server.getPort(), properties, clientBundles);
        try {
            bindRequest("");
            var api = context.getBean(LoopbackAgentApiService.class);
            assertThat(api.send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(204);
            context.close();
            assertThatThrownBy(() -> api.send(HttpMethod.GET, "/api/core/context-test", null)).isInstanceOf(ResourceAccessException.class);
            serverBundles.updateBundle("server", replacement);
            clientBundles.updateBundle("server", replacement);

            assertThatThrownBy(() -> api.send(HttpMethod.GET, "/api/core/context-test", null)).isInstanceOf(ResourceAccessException.class);
            assertThat(received).hasValue(1);
        }
        finally {
            context.close();
            RequestContextHolder.resetRequestAttributes();
            server.stop();
            server.destroy();
        }
    }

    private WebServer rotatingTlsServer(SslBundles bundles, AtomicInteger received) {
        var factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(InetAddress.ofLiteral("127.0.0.1"));
        factory.setSsl(Ssl.forBundle("server"));
        factory.setSslBundles(bundles);
        var server = factory.getWebServer(servletContext -> servletContext.addServlet("api", new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) {
                received.incrementAndGet();
                response.setHeader(HttpHeaders.CONNECTION, "close");
                response.setStatus(TOKEN.equals(request.getHeader(HttpHeaders.AUTHORIZATION)) ? 204 : 401);
            }
        }).addMapping("/*"));
        server.start();
        return server;
    }

    @Test
    void rejectsAnUnrelatedCertificateBeforeSendingTheToken() throws Exception {
        var properties = new ServerProperties();
        properties.setSsl(developmentSsl());
        var unrelated = sslBundle(serverKeyStore(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)));

        assertTlsRejected(properties, new DefaultSslBundleRegistry(), unrelated);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rejectsExpiredAndNotYetValidPinnedCertificates(boolean expired) throws Exception {
        Instant now = Instant.now();
        var bundle = sslBundle(serverKeyStore(expired ? now.minusSeconds(3600) : now.plusSeconds(3600), expired ? now.minusSeconds(60) : now.plusSeconds(7200)));
        var registry = new DefaultSslBundleRegistry();
        registry.registerBundle("server", bundle);
        var properties = new ServerProperties();
        properties.setSsl(Ssl.forBundle("server"));

        assertTlsRejected(properties, registry, bundle);
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://127.0.0.1", "//127.0.0.1" })
    void rejectsAbsoluteAndAuthorityRelativeApiUrls(String prefix) throws Exception {
        var received = new AtomicInteger();
        var server = httpServer(received);
        server.start();
        try (var context = clientContext(server.getAddress().getPort(), new ServerProperties())) {
            bindRequest("");
            var api = context.getBean(LoopbackAgentApiService.class);
            String path = prefix + ":" + server.getAddress().getPort() + "/api/core/context-test";

            assertThatThrownBy(() -> api.send(HttpMethod.GET, path, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> api.sendText(HttpMethod.PATCH, path, MARKDOWN)).isInstanceOf(IllegalArgumentException.class);
            assertThat(received).hasValue(0);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop(0);
        }
    }

    @Test
    void rejectsAContextPathThatWouldChangeTheAuthority() throws Exception {
        var received = new AtomicInteger();
        var server = httpServer(received);
        server.start();
        try (var context = clientContext(server.getAddress().getPort(), new ServerProperties())) {
            bindRequest("@127.0.0.1:" + server.getAddress().getPort());

            assertThatThrownBy(() -> context.getBean(LoopbackAgentApiService.class).send(HttpMethod.GET, "/api/core/context-test", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(received).hasValue(0);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop(0);
        }
    }

    @Test
    void doesNotFollowRedirectsToAnotherConnector() throws Exception {
        var received = new AtomicInteger();
        var target = httpServer(received);
        var source = HttpServer.create(new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 0), 0);
        source.createContext("/api/core/context-test", exchange -> {
            exchange.getResponseHeaders().add(HttpHeaders.LOCATION, "http://127.0.0.1:" + target.getAddress().getPort() + "/api/core/context-test");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        target.start();
        source.start();
        try (var context = clientContext(source.getAddress().getPort(), new ServerProperties())) {
            bindRequest("");

            assertThat(context.getBean(LoopbackAgentApiService.class).send(HttpMethod.GET, "/api/core/context-test", null).status()).isEqualTo(302);
            assertThat(received).hasValue(0);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            source.stop(0);
            target.stop(0);
        }
    }

    private void assertTlsRejected(ServerProperties properties, SslBundles registry, SslBundle presented) throws Exception {
        var received = new AtomicInteger();
        var server = httpsServer(presented);
        server.createContext("/api/core/context-test", exchange -> {
            received.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try (var context = clientContext(server.getAddress().getPort(), properties, registry)) {
            bindRequest("");

            assertThatThrownBy(() -> context.getBean(LoopbackAgentApiService.class).send(HttpMethod.GET, "/api/core/context-test", null))
                    .hasCauseInstanceOf(SSLHandshakeException.class);
            assertThat(received).hasValue(0);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop(0);
        }
    }

    private HttpServer httpServer(AtomicInteger received) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 0), 0);
        server.createContext("/api/core/context-test", exchange -> {
            received.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        return server;
    }

    private HttpsServer httpsServer(SslBundle bundle) throws IOException {
        var server = HttpsServer.create(new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(bundle.createSslContext()));
        return server;
    }

    private Ssl developmentSsl() {
        var ssl = new Ssl();
        ssl.setKeyStore("classpath:config/tls/keystore.p12");
        ssl.setKeyStorePassword("password");
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyAlias("selfsigned");
        return ssl;
    }

    private KeyStore serverKeyStore(Instant notBefore, Instant notAfter) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key = generator.generateKeyPair();
        var subject = new X500Name("CN=artemis.example.org");
        var certificateBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, Date.from(notBefore), Date.from(notAfter), subject, key.getPublic());
        certificateBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.dNSName, "artemis.example.org")));
        X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(certificateBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(key.getPrivate())));
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", key.getPrivate(), "password".toCharArray(), new Certificate[] { certificate });
        return keyStore;
    }

    private SslBundle sslBundle(KeyStore keyStore) {
        return SslBundle.of(SslStoreBundle.of(keyStore, "password", null), SslBundleKey.of("password", "server"));
    }

    private String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(bytes) + "\n-----END " + type + "-----\n";
    }

    private void bindRequest(String contextPath) {
        var servedRequest = new MockHttpServletRequest();
        servedRequest.setContextPath(contextPath);
        servedRequest.setSecure(true);
        servedRequest.setServerName("untrusted.example.org");
        servedRequest.addHeader(HttpHeaders.HOST, "untrusted.example.org");
        servedRequest.addHeader("Forwarded", "host=untrusted.example.org;proto=https");
        servedRequest.addHeader("X-Forwarded-Host", "untrusted.example.org");
        servedRequest.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servedRequest));
    }

    private AnnotationConfigApplicationContext clientContext(int port, ServerProperties properties) {
        return clientContext(port, properties, new DefaultSslBundleRegistry());
    }

    private AnnotationConfigApplicationContext clientContext(int port, ServerProperties properties, SslBundles registry) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("core");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("loopback-test", Map.of("local.server.port", port, "artemis.agent-access.enabled", true)));
        context.registerBean(JsonMapper.class, JsonObjectMapper::get);
        context.registerBean(ServerProperties.class, () -> properties);
        context.registerBean(SslBundles.class, () -> registry);
        context.registerBean(LoopbackAgentApiService.class);
        context.refresh();
        return context;
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/", "/artemis" })
    void loopbackPreservesTheServedContextPathForJsonAndPlainText(String contextPath) throws Exception {
        String markdown = "# Grüße: λ 中文 — mathematical explanations";
        String apiPath = "/api/core/context-test";
        String expectedPath = (contextPath.equals("/") ? "" : contextPath) + apiPath;
        var server = HttpServer.create(new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 0), 0);
        server.createContext(expectedPath, exchange -> {
            boolean valid = "Bearer art_pat_context_test".equals(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    && expectedPath.equals(exchange.getRequestURI().getPath());
            if (exchange.getRequestMethod().equals("PATCH")) {
                valid &= MediaType.parseMediaType(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isCompatibleWith(MediaType.TEXT_PLAIN)
                        && markdown.equals(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 400, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try (var context = clientContext(server.getAddress().getPort(), new ServerProperties())) {
            bindRequest(contextPath);
            var api = context.getBean(LoopbackAgentApiService.class);

            assertThat(api.send(HttpMethod.GET, apiPath, null).status()).isEqualTo(200);
            assertThat(api.sendText(HttpMethod.PATCH, apiPath, markdown).status()).isEqualTo(200);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop(0);
        }
    }

    private record TextPayload(String text) {
    }
}
