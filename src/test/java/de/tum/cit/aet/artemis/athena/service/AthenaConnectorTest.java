package de.tum.cit.aet.artemis.athena.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import de.tum.cit.aet.artemis.core.exception.NetworkingException;

class AthenaConnectorTest {

    private static final String ATHENA_URL = "https://athena.example.com/feedback_suggestions";

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "UNPROCESSABLE_CONTENT", "INTERNAL_SERVER_ERROR" })
    void wrapsHttpErrorsAsNetworkingExceptions(HttpStatus status) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ATHENA_URL)).andRespond(withStatus(status));
        var connector = new AthenaConnector<String, String>(builder.build(), String.class);

        assertThatExceptionOfType(NetworkingException.class).isThrownBy(() -> connector.invokeWithRetry(ATHENA_URL, "{}", 0))
                .withRootCauseInstanceOf(RestClientResponseException.class);

        server.verify();
    }
}
