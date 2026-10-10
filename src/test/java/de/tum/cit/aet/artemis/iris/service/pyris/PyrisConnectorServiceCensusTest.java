package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;

/**
 * Tests how {@link PyrisConnectorService#getIngestionCensus} handles the census responses and failures it can meet; every failure means
 * "census unavailable" for the caller.
 */
class PyrisConnectorServiceCensusTest {

    private static final String PYRIS_URL = "http://pyris.test";

    private RestTemplate censusRestTemplate;

    private MockRestServiceServer server;

    private PyrisConnectorService connector;

    @BeforeEach
    void setUp() {
        censusRestTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(censusRestTemplate).build();
        connector = connectorFor(censusRestTemplate, PYRIS_URL);
    }

    private static PyrisConnectorService connectorFor(RestTemplate censusTemplate, String pyrisUrl) {
        PyrisConnectorService service = new PyrisConnectorService(new RestTemplate(), censusTemplate, JsonObjectMapper.get());
        ReflectionTestUtils.setField(service, "pyrisUrl", pyrisUrl);
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.test");
        return service;
    }

    /** The base URL is encoded exactly once, so Pyris reads back the URL Artemis announces in its health check. */
    @Test
    void shouldEncodeTheBaseUrlExactlyOnce() {
        server.expect(ExpectedCount.once(), requestTo(PYRIS_URL + "/api/v1/courses/7/ingestion-census?base_url=https%3A%2F%2Fartemis.test")).andRespond(withSuccess("""
                {"courseId": 7, "currentPipelineVersion": 3, "truncated": false, "units": []}
                """, MediaType.APPLICATION_JSON));

        assertThat(connector.getIngestionCensus(7)).isNotNull();
        server.verify();
    }

    @Test
    void shouldParseTheCensusIncludingBothTruncationFlags() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(PYRIS_URL + "/api/v1/courses/7/ingestion-census?base_url="))).andRespond(withSuccess("""
                {"courseId": 7, "currentPipelineVersion": 3, "truncated": true, "units": [
                  {"lectureId": 1, "lectureUnitId": 100, "unitRowCount": 1, "chunkCount": 4, "generationCount": 1, "transcriptionCount": 2,
                   "segmentCount": 4, "missingPageCount": 0, "nullDisplayCount": 0, "truncated": true}
                ]}
                """, MediaType.APPLICATION_JSON));

        IngestionCensusDTO census = connector.getIngestionCensus(7);

        assertThat(census).isNotNull();
        assertThat(census.truncated()).as("course-level truncation is read").isTrue();
        assertThat(census.units()).singleElement().satisfies(unit -> {
            assertThat(unit.lectureUnitId()).isEqualTo(100L);
            assertThat(unit.transcriptionCount()).isEqualTo(2);
            assertThat(unit.truncated()).as("unit-level truncation is read").isTrue();
        });
        server.verify();
    }

    @Test
    void shouldTreatAMissingEndpointAsUnavailable() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(PYRIS_URL))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(connector.getIngestionCensus(7)).isNull();
    }

    @Test
    void shouldTreatAServerErrorAsUnavailable() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(PYRIS_URL))).andRespond(withServerError());

        assertThat(connector.getIngestionCensus(7)).isNull();
    }

    /** A census that never answers ends at the read timeout of the census rest template and counts as unavailable. */
    @Test
    void shouldGiveUpOnACensusThatNeverAnswers() throws Exception {
        try (ServerSocket silentServer = new ServerSocket(0)) {
            // Accept the connection and keep it open without ever sending a response
            CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
                try {
                    return silentServer.accept();
                }
                catch (Exception e) {
                    return null;
                }
            });
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(1000);
            factory.setReadTimeout(300);
            PyrisConnectorService slowConnector = connectorFor(new RestTemplate(factory), "http://localhost:" + silentServer.getLocalPort());

            long start = System.nanoTime();
            IngestionCensusDTO census = slowConnector.getIngestionCensus(7);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(census).as("a timed-out census is unavailable").isNull();
            assertThat(elapsedMillis).as("the call ends at the read timeout instead of hanging").isLessThan(5000);
            Socket socket = accepted.get(5, TimeUnit.SECONDS);
            if (socket != null) {
                socket.close();
            }
        }
    }
}
