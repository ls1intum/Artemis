package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sun.net.httpserver.HttpServer;

class PublishedFingerprintsFetcherTest {

    @ParameterizedTest
    @CsvSource({ "https://artemis.example.org, https://artemis.example.org/api/localvc/public/ssh-fingerprints",
            "https://artemis.example.org/, https://artemis.example.org/api/localvc/public/ssh-fingerprints",
            "https://example.org/artemis, https://example.org/artemis/api/localvc/public/ssh-fingerprints",
            "https://example.org/artemis/, https://example.org/artemis/api/localvc/public/ssh-fingerprints",
            "https://example.org:8443/a/b/, https://example.org:8443/a/b/api/localvc/public/ssh-fingerprints",
            "https://example.org/artemis%2Dprod/, https://example.org/artemis%2Dprod/api/localvc/public/ssh-fingerprints",
            "https://example.org/artemis?x=1#top, https://example.org/artemis/api/localvc/public/ssh-fingerprints" })
    void theFingerprintsAreReadBelowThePathOfTheConfiguredUrl(String base, String expected) {
        assertThat(new PublishedFingerprintsFetcher(URI.create(base)).uri()).hasToString(expected);
    }

    @Test
    void nothingIsRequestedOverPlainHttp() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"hostKeys\":[{\"algorithm\":\"Ed25519\",\"fingerprint\":\"SHA256:abc\"}]}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var fetcher = new PublishedFingerprintsFetcher(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

            assertThat(fetcher.get()).as("fingerprints that arrive over an unprotected channel prove nothing").isEmpty();
            assertThat(requests).as("and they are not even asked for").hasValue(0);
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void everyPublishedFingerprintIsReadAlsoSeveralOfOneAlgorithm() throws Exception {
        String published = fetchedFrom(200, """
                {"hostKeys":[{"algorithm":"RSA","fingerprint":"SHA256:a"},{"algorithm":"RSA","fingerprint":"SHA256:b"},{"algorithm":"Ed25519","fingerprint":"SHA256:c"}]}""");

        assertThat(published).isEqualTo("SHA256:a,SHA256:b,SHA256:c");
    }

    @Test
    void fieldsThatAnotherVersionAddsAreIgnored() throws Exception {
        String published = fetchedFrom(200, """
                {"hostKeys":[{"algorithm":"RSA","fingerprint":"SHA256:a","validUntil":"2030-01-01"}],"nodeId":3}""");

        assertThat(published).isEqualTo("SHA256:a");
    }

    @Test
    void theFormatOfAnOlderVersionIsNotReadAsPublishedFingerprints() throws Exception {
        // fingerprints that cannot be read are the case that the setting of the agent decides, so nothing may be taken from a body in another shape
        assertThat(fetchedFrom(200, "{\"RSA\":[\"SHA256:a\"]}")).isEmpty();
        assertThat(fetchedFrom(200, "{}")).isEmpty();
        assertThat(fetchedFrom(200, "null")).isEmpty();
        assertThat(fetchedFrom(200, "{\"hostKeys\":[null,{\"algorithm\":\"RSA\"}]}")).isEmpty();
    }

    @Test
    void nothingIsReadFromAnErrorAnswer() throws Exception {
        assertThat(fetchedFrom(503, "{\"hostKeys\":[{\"algorithm\":\"RSA\",\"fingerprint\":\"SHA256:a\"}]}")).isEmpty();
    }

    /** Serves the body with the status and returns what the fetcher reads from it, sorted and joined by commas. */
    private static String fetchedFrom(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            // fetch does not check the scheme, which get() does, so that a plain server can be used here
            return new PublishedFingerprintsFetcher(URI.create("http://127.0.0.1:" + server.getAddress().getPort())).fetch(Duration.ofSeconds(5)).stream().sorted()
                    .collect(Collectors.joining(","));
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void aBodyThatStallsAfterTheHeadersDoesNotBlockLongerThanTheDeadline() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                release.await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            var fetcher = new PublishedFingerprintsFetcher(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            long start = System.nanoTime();

            assertThat(fetcher.fetch(Duration.ofMillis(500))).isEmpty();

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        }
        finally {
            release.countDown();
            server.stop(0);
        }
    }
}
