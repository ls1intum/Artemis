package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;

/**
 * Starts a real embedded Tomcat with the Artemis customizer, because the locale limit is a connector setting that a mock cannot exercise.
 */
class TomcatConfigurationTest {

    private WebServer webServer;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() {
        var factory = new TomcatServletWebServerFactory(0);
        new TomcatConfiguration().servletContainerCustomizer().customize(factory);
        webServer = factory.getWebServer(servletContext -> servletContext.addServlet("locale", new LocaleServlet()).addMapping("/*"));
        webServer.start();
    }

    @AfterEach
    void stopServer() {
        webServer.stop();
    }

    @Test
    void requestWithTenLocalesIsServed() throws Exception {
        assertThat(statusForAcceptLanguageWithEntries(10)).isEqualTo(200);
    }

    @Test
    void requestWithMoreLocalesThanTomcatsDefaultIsServed() throws Exception {
        assertThat(statusForAcceptLanguageWithEntries(15)).isEqualTo(200);
    }

    @Test
    void requestAtTheConfiguredLimitIsServed() throws Exception {
        assertThat(statusForAcceptLanguageWithEntries(TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES)).isEqualTo(200);
    }

    @Test
    void requestAboveTheConfiguredLimitIsRejected() throws Exception {
        assertThat(statusForAcceptLanguageWithEntries(TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES + 1)).isEqualTo(500);
    }

    private int statusForAcceptLanguageWithEntries(int entries) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + webServer.getPort() + "/")).header("Accept-Language", acceptLanguageWithEntries(entries)).GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    /**
     * Builds a header like a browser with many configured languages sends it: distinct language tags with falling quality values.
     */
    private static String acceptLanguageWithEntries(int entries) {
        return IntStream.range(0, entries).mapToObj(index -> {
            String language = "" + (char) ('a' + index / 26 % 26) + (char) ('a' + index % 26);
            return index == 0 ? language : language + ";q=0." + Math.max(1, 9 - index % 9);
        }).collect(Collectors.joining(","));
    }

    /**
     * Like Spring's {@code RequestContextFilter}, which resolves the locale of every request.
     */
    private static class LocaleServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write(request.getLocale().toLanguageTag());
        }
    }
}
