package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
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
    void requestWithTenLocalesIsServedWithAllOfThem() throws Exception {
        assertServedWithLocales(10, 10);
    }

    @Test
    void requestWithMoreLocalesThanTomcatsDefaultIsServedWithAllOfThem() throws Exception {
        assertServedWithLocales(15, 15);
    }

    @Test
    void requestAtTheConfiguredLimitIsServedWithAllOfTheLocales() throws Exception {
        assertServedWithLocales(TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES, TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES);
    }

    /**
     * Tomcat 11.0.27 threw for a header above the limit, which made every request of such a client fail with a 500. Tomcat 11.0.28 ignores
     * the extra locales, so the request is served with the locales up to the limit.
     */
    @Test
    void requestAboveTheConfiguredLimitIsServedAndTheExtraLocalesAreIgnored() throws Exception {
        assertServedWithLocales(TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES + 1, TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES);
    }

    @Test
    void requestFarAboveTheConfiguredLimitIsServedWithTheLocalesUpToTheLimit() throws Exception {
        assertServedWithLocales(TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES * 10, TomcatConfiguration.MAX_ACCEPT_LANGUAGE_LOCALES);
    }

    /**
     * Sends a header with the given number of language entries and checks that the request is served and how many of the locales the
     * servlet could read, which the limit has to cap.
     */
    private void assertServedWithLocales(int entries, int expectedRetainedLocales) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + webServer.getPort() + "/")).header("Accept-Language", acceptLanguageWithEntries(entries)).GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("status for %d entries", entries).isEqualTo(200);
        assertThat(Integer.parseInt(response.body())).as("locales retained for %d entries", entries).isEqualTo(expectedRetainedLocales);
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
     * Resolves the locale of every request like Spring's {@code RequestContextFilter}, which Tomcat 11.0.27 made fail above the limit,
     * and answers with the number of locales that it could read.
     */
    private static class LocaleServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            request.getLocale();
            response.getWriter().write(String.valueOf(Collections.list(request.getLocales()).size()));
        }
    }
}
