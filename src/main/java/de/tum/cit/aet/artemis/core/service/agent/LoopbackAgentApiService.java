package de.tum.cit.aet.artemis.core.service.agent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.Inet6Address;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerSslBundle;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;

/**
 * Calls the REST API of this server over the loopback interface, with the token of the request being served.
 * <p>
 * The call runs through the whole security chain again, so the token is checked, its scopes and the role of its owner apply, and the
 * call shows up in the audit log like any other.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@Service
public class LoopbackAgentApiService implements AgentApi {

    /** Creating a programming exercise sets up repositories and a build plan, which takes a while. */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    private final JsonMapper jsonMapper;

    private final URI origin;

    public LoopbackAgentApiService(JsonMapper jsonMapper, @Value("${local.server.port:${server.port:8080}}") int port, ServerProperties serverProperties,
            @Nullable SslBundles sslBundles) {
        this.jsonMapper = jsonMapper;
        var ssl = serverProperties.getSsl();
        boolean tls = Ssl.isEnabled(ssl);
        String host = serverProperties.getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        this.origin = URI.create((tls ? "https" : "http") + "://" + host + ":" + port);
        // The JDK client, because the one behind SimpleClientHttpRequestFactory cannot send PATCH. HTTP/1.1, because the server does not
        // speak cleartext HTTP/2 and the upgrade the JDK client attempts by default would only add a round trip.
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(CONNECT_TIMEOUT).proxy(HttpClient.Builder.NO_PROXY)
                .followRedirects(HttpClient.Redirect.NEVER);
        if (tls) {
            client.sslContext(loopbackSslContext(WebServerSslBundle.get(ssl, sslBundles)));
        }
        var requestFactory = new JdkClientHttpRequestFactory(client.build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public AgentApiResponse send(HttpMethod method, String path, @Nullable Object body) {
        var request = restClient.method(method).uri(loopbackUri(path)).header(HttpHeaders.AUTHORIZATION, authorization()).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
        }
        return execute(request);
    }

    @Override
    public AgentApiResponse sendText(HttpMethod method, String path, String text) {
        var request = restClient.method(method).uri(loopbackUri(path)).header(HttpHeaders.AUTHORIZATION, authorization()).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)).body(text);
        return execute(request);
    }

    private AgentApiResponse execute(RestClient.RequestBodySpec request) {
        return request.exchange((clientRequest, response) -> {
            String text = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            return AgentApiResponse.of(jsonMapper, response.getStatusCode().value(), text);
        });
    }

    private URI loopbackUri(String path) {
        if (!path.startsWith("/api/")) {
            throw new IllegalArgumentException("An agent API path must start with /api/");
        }
        URI uri = URI.create(origin + contextPath() + path);
        if (!origin.getScheme().equals(uri.getScheme()) || !origin.getHost().equals(uri.getHost()) || origin.getPort() != uri.getPort() || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("An agent API call must stay on this server's loopback connector");
        }
        return uri;
    }

    private SSLContext loopbackSslContext(SslBundle bundle) {
        try {
            var keyStore = bundle.getStores().getKeyStore();
            if (keyStore == null) {
                throw new IllegalStateException("The TLS connector has no key store for loopback certificate pinning");
            }
            String configuredAlias = bundle.getKey().getAlias();
            Set<X509Certificate> certificates = new HashSet<>();
            var aliases = configuredAlias == null ? Collections.list(keyStore.aliases()) : List.of(configuredAlias);
            for (String alias : aliases) {
                if (keyStore.isKeyEntry(alias) && keyStore.getCertificate(alias) instanceof X509Certificate certificate) {
                    certificates.add(certificate);
                }
            }
            if (certificates.isEmpty()) {
                throw new IllegalStateException("The TLS connector has no server certificate for loopback certificate pinning");
            }
            SSLContext context = SSLContext.getInstance(bundle.getProtocol());
            context.init(null, new TrustManager[] { new LoopbackCertificateTrustManager(Set.copyOf(certificates)) }, null);
            return context;
        }
        catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not configure TLS for the agent loopback client", exception);
        }
    }

    /**
     * This client knows the server's exact certificate, so that certificate is its identity even when its SAN names the public host
     * rather than loopback (or the development certificate has no SAN). This trust policy is private to the loopback client.
     */
    private static final class LoopbackCertificateTrustManager extends X509ExtendedTrustManager {

        private final Set<X509Certificate> certificates;

        private LoopbackCertificateTrustManager(Set<X509Certificate> certificates) {
            this.certificates = certificates;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0 || authType == null || authType.isEmpty()) {
                throw new CertificateException("Missing server certificate or authentication type");
            }
            chain[0].checkValidity();
            if (!certificates.contains(chain[0])) {
                throw new CertificateException("The certificate does not belong to this server's TLS connector");
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("The loopback client does not authenticate TLS clients");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return certificates.toArray(X509Certificate[]::new);
        }
    }

    /** Keeps internal calls in the same servlet context as the MCP request, including deployments below a path prefix. */
    private String contextPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String path = attributes.getRequest().getContextPath();
            return "/".equals(path) ? "" : path;
        }
        return "";
    }
}
