package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
            byte[] body = "{\"ssh-ed25519\":[\"SHA256:abc\"]}".getBytes();
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
