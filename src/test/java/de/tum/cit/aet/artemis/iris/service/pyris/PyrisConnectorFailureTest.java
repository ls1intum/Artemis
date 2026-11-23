package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;

class PyrisConnectorFailureTest {

    private static final String SEARCH_URL = "http://pyris.test/api/v1/search/lectures";

    private MockRestServiceServer server;

    private PyrisConnectorService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new PyrisConnectorService(builder.build(), RestClient.create(), JsonObjectMapper.get(), mock(PyrisCallbackTokenService.class));
        ReflectionTestUtils.setField(service, "pyrisUrl", "http://pyris.test");
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "http://artemis.test");
    }

    @ParameterizedTest
    @ValueSource(ints = { 204, 302 })
    void unusableSearchResponsesProduceEmptyCollections(int status) {
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        assertThat(service.searchLectures("Java", 5, null, null, null)).isEmpty();
        server.verify();
    }

    @Test
    void searchTransportFailuresHaveOperationSpecificMessages() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withException(new IOException("timeout")));
        assertThatThrownBy(() -> service.searchLectures("Java", 5, null, null, null)).isInstanceOf(PyrisConnectorException.class)
                .hasMessage("Could not fetch lecture search results from Pyris");
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 500 })
    void jsonNullErrorBodyOnlyRelaysTheStatus(int status) {
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)).body("null").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.searchLectures("Java", 5, null, null, null)).isExactlyInstanceOf(IrisInternalPyrisErrorException.class)
                .satisfies(error -> assertThat(((IrisInternalPyrisErrorException) error).getTranslationParams()).containsEntry("pyrisErrorMessage",
                        status + " " + HttpStatus.valueOf(status).getReasonPhrase()));
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 500 })
    void detailOfThePyrisErrorIsNotRelayedToTheUser(int status) {
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .body("{\"detail\":{\"errorMessage\":\"internal host db-7 failed with token abc\"}}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.searchLectures("Java", 5, null, null, null)).isExactlyInstanceOf(IrisInternalPyrisErrorException.class)
                .satisfies(error -> assertThat(((IrisInternalPyrisErrorException) error).getTranslationParams().get("pyrisErrorMessage"))
                        .isEqualTo(status + " " + HttpStatus.valueOf(status).getReasonPhrase()));
    }
}
