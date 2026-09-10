package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class LinkPreviewDocumentFetcherTest {

    private HttpServer httpServer;

    private InetAddress serverAddress;

    @BeforeEach
    void startHttpServer() throws IOException {
        serverAddress = InetAddress.getByName("127.0.0.1");
        httpServer = HttpServer.create(new InetSocketAddress(serverAddress, 0), 0);
        httpServer.start();
    }

    @AfterEach
    void stopHttpServer() {
        httpServer.stop(0);
    }

    @Test
    void validatesAndRetrievesRedirectDestination() throws Exception {
        String targetUrl = url("/target");
        AtomicReference<String> requestHost = new AtomicReference<>();
        httpServer.createContext("/redirect", exchange -> redirect(exchange, targetUrl));
        httpServer.createContext("/target", exchange -> {
            requestHost.set(exchange.getRequestHeaders().getFirst("Host"));
            respond(exchange, "<html><head><meta property=\"og:title\" content=\"Example\"></head></html>");
        });
        List<URI> resolvedUris = new ArrayList<>();

        var document = LinkPreviewDocumentFetcher.fetch(url("/redirect"), uri -> {
            resolvedUris.add(uri);
            return validatedUrl(uri);
        });

        assertThat(resolvedUris).extracting(URI::getPath).containsExactly("/redirect", "/target");
        assertThat(document.selectFirst("meta[property=og:title]").attr("content")).isEqualTo("Example");
        assertThat(requestHost).hasValue("example.com:" + httpServer.getAddress().getPort());
    }

    @Test
    void stopsWhenRedirectDestinationIsRejected() throws Exception {
        String targetUrl = url("/target");
        AtomicInteger targetRequests = new AtomicInteger();
        httpServer.createContext("/redirect", exchange -> redirect(exchange, targetUrl));
        httpServer.createContext("/target", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, "<html></html>");
        });

        assertThatThrownBy(() -> LinkPreviewDocumentFetcher.fetch(url("/redirect"), uri -> {
            if (uri.getPath().equals("/target")) {
                throw new IOException("Rejected link preview URL");
            }
            return validatedUrl(uri);
        })).isInstanceOf(IOException.class);
        assertThat(targetRequests).hasValue(0);
    }

    @Test
    void stopsAfterMaximumNumberOfRedirects() {
        AtomicInteger requests = new AtomicInteger();
        httpServer.createContext("/redirect", exchange -> {
            requests.incrementAndGet();
            redirect(exchange, url("/redirect"));
        });

        assertThatThrownBy(() -> LinkPreviewDocumentFetcher.fetch(url("/redirect"), this::validatedUrl)).isInstanceOf(IOException.class)
                .hasMessage("Too many redirects while retrieving link preview");
        assertThat(requests).hasValue(6);
    }

    @Test
    void rejectsResponseExceedingMaximumSize() {
        httpServer.createContext("/large", exchange -> respond(exchange, 200, new byte[2 * 1024 * 1024 + 1]));

        assertThatThrownBy(() -> LinkPreviewDocumentFetcher.fetch(url("/large"), this::validatedUrl)).isInstanceOf(IOException.class)
                .hasMessage("Link preview response exceeded the maximum size");
    }

    @Test
    void rejectsUnsuccessfulResponse() {
        httpServer.createContext("/error", exchange -> respond(exchange, 500, "Error".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> LinkPreviewDocumentFetcher.fetch(url("/error"), this::validatedUrl)).isInstanceOf(IOException.class)
                .hasMessage("Link preview request returned status 500");
    }

    @Test
    void retrievesChunkedResponse() throws Exception {
        httpServer.createContext("/chunked", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write("<html><head>".getBytes(StandardCharsets.UTF_8));
                responseBody.flush();
                responseBody.write("<meta property=\"og:title\" content=\"Example\"></head></html>".getBytes(StandardCharsets.UTF_8));
            }
        });

        var document = LinkPreviewDocumentFetcher.fetch(url("/chunked"), this::validatedUrl);

        assertThat(document.selectFirst("meta[property=og:title]").attr("content")).isEqualTo("Example");
    }

    @Test
    void boundsResponseBodyReadDuration() {
        httpServer.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var responseBody = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    responseBody.write('a');
                    responseBody.flush();
                    Thread.sleep(25);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (IOException ignored) {
                // The client closes the response when the request duration is exceeded.
            }
        });

        assertThatThrownBy(() -> LinkPreviewDocumentFetcher.fetch(url("/slow"), this::validatedUrl, Duration.ofMillis(500))).isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void boundsEntireRedirectChainDuration() {
        httpServer.createContext("/slow-redirect", exchange -> {
            try {
                Thread.sleep(300);
                redirect(exchange, url("/slow-redirect"));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (IOException ignored) {
                // The client closes the response when the redirect chain duration is exceeded.
            }
        });

        assertThatThrownBy(() -> LinkPreviewDocumentFetcher.fetch(url("/slow-redirect"), this::validatedUrl, Duration.ofMillis(500))).isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
    }

    private String url(String path) {
        return "http://example.com:" + httpServer.getAddress().getPort() + path;
    }

    private LinkPreviewUrlValidator.ValidatedUrl validatedUrl(URI uri) {
        return new LinkPreviewUrlValidator.ValidatedUrl(uri, List.of(serverAddress));
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }
}
