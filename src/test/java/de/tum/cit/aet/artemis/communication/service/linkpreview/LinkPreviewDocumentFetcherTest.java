package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        httpServer.createContext("/redirect", exchange -> redirect(exchange, targetUrl));
        httpServer.createContext("/target", exchange -> respond(exchange, "<html><head><meta property=\"og:title\" content=\"Example\"></head></html>"));
        List<URI> resolvedUris = new ArrayList<>();

        var document = LinkPreviewDocumentFetcher.fetch(url("/redirect"), uri -> {
            resolvedUris.add(uri);
            return new LinkPreviewUrlValidator.ValidatedUrl(uri);
        });

        assertThat(resolvedUris).extracting(URI::getPath).containsExactly("/redirect", "/target");
        assertThat(document.selectFirst("meta[property=og:title]").attr("content")).isEqualTo("Example");
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
            return new LinkPreviewUrlValidator.ValidatedUrl(uri);
        })).isInstanceOf(IOException.class);
        assertThat(targetRequests).hasValue(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + path;
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }
}
