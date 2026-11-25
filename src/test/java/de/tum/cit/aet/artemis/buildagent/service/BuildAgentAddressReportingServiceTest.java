package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/**
 * Tests how the build agent asks a core node for the address it is seen at, against a local server.
 */
class BuildAgentAddressReportingServiceTest {

    private static final String PATH = "/api/localvc/public/observed-client-address";

    private HttpServer server;

    private final AtomicReference<String> requestedPath = new AtomicReference<>();

    private BuildAgentAddressReportingService service;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service = new BuildAgentAddressReportingService(mock(DistributedDataAccessService.class), JsonObjectMapper.get());
        ReflectionTestUtils.setField(service, "localVCBaseUri", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void serve(String path, int status, String body) {
        server.createContext(path, exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private String measure() {
        return ReflectionTestUtils.invokeMethod(service, "measureObservedAddress");
    }

    @Test
    void theAddressThatTheCoreNodeReportsIsReturned() {
        serve(PATH, 200, "{\"address\":\"203.0.113.7\"}");

        assertThat(measure()).isEqualTo("203.0.113.7");
        assertThat(requestedPath).hasValue(PATH);
    }

    @Test
    void anAnswerOtherThanOkIsNoAddress() {
        serve(PATH, 503, "{\"address\":\"203.0.113.7\"}");

        assertThat(measure()).isNull();
    }

    @Test
    void aBodyWithoutAnAddressIsNoAddress() {
        serve(PATH, 200, "{}");

        assertThat(measure()).isNull();
    }

    @Test
    void aRedirectIsFollowed() {
        // A reverse proxy in front of the core node may redirect, like the client of this service always allowed
        server.createContext(PATH, exchange -> {
            exchange.getResponseHeaders().add("Location", "/moved");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        serve("/moved", 200, "{\"address\":\"198.51.100.2\"}");

        assertThat(measure()).isEqualTo("198.51.100.2");
    }
}
