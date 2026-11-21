package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.athena.config.AthenaAuthorizationInterceptor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

/**
 * Checks the settings of {@link RestClientConfiguration} that the services depend on and that no other test sees, because the mock servers of the
 * other tests replace the request factory of the client.
 */
class RestClientConfigurationTest {

    private final RestClientConfiguration configuration = new RestClientConfiguration();

    @Test
    void theClientOfAServiceWithAnInterceptorDoesNotReadXml() {
        var interceptor = new AthenaAuthorizationInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", "athena-secret");
        ReflectionTestUtils.setField(interceptor, "artemisServerUrl", "https://artemis.test");
        // A copy of the client keeps its converters, its interceptor and its URI encoding, and gets a mock request factory.
        RestClient.Builder builder = configuration.athenaRestClient(RestClient.builder(), interceptor).mutate();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://athena.test/health")).andExpect(request -> {
            // the interceptor of the service is part of the client
            assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("athena-secret");
            assertThat(request.getHeaders().getFirst("X-Server-URL")).isEqualTo("https://artemis.test");
        }).andRespond(withSuccess("<health><status>ok</status></health>", MediaType.APPLICATION_XML));
        var client = builder.build();

        assertThatThrownBy(() -> client.get().uri("https://athena.test/health").retrieve().body(Map.class)).isInstanceOf(RestClientException.class);
    }

    @Test
    void theClientOfAServiceWithoutAnInterceptorStillReadsXml() {
        // The counterpart of the test above, which shows that the XML converter is on the class path, so that its absence there means something.
        RestClient.Builder builder = configuration.hermesRestClient(RestClient.builder()).mutate();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://hermes.test/api/health")).andRespond(withSuccess("<health><status>ok</status></health>", MediaType.APPLICATION_XML));

        @SuppressWarnings("unchecked")
        Map<String, Object> health = builder.build().get().uri("https://hermes.test/api/health").retrieve().body(Map.class);

        assertThat(health).containsEntry("status", "ok");
    }

    @Test
    void aTemplateIsEncodedAfterItsExpansionLikeTheRestTemplateDidIt() {
        // The Sharing and Pyris call sites pass strings with encoded parts, which the template encoded once more.
        RestClient.Builder builder = configuration.osvRestClient(RestClient.builder()).mutate();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://osv.test/a%2520b?x=%252F")).andRespond(withSuccess());

        builder.build().get().uri("https://osv.test/a%20b?x=%2F").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void theSharingClientRecordsNoObservationBecauseItsUrlsCarryTheApiKey() {
        List<String> observed = new ArrayList<>();
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {

            @Override
            public void onStart(Observation.Context context) {
                observed.add(context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });

        RestClient.Builder sharingBuilder = configuration.sharingRestClient(RestClient.builder().observationRegistry(registry)).mutate();
        var sharingServer = MockRestServiceServer.bindTo(sharingBuilder).build();
        sharingServer.expect(requestTo("https://sharing.test/api/pluginIF/v0.1/reInitialize?apiKey=secret-key")).andRespond(withSuccess());
        sharingBuilder.build().get().uri("https://sharing.test/api/pluginIF/v0.1/reInitialize?apiKey=secret-key").retrieve().toBodilessEntity();
        sharingServer.verify();

        assertThat(observed).isEmpty();

        // The counterpart: another client does record its requests with the same registry, so the absence above means something.
        RestClient.Builder osvBuilder = configuration.osvRestClient(RestClient.builder().observationRegistry(registry)).mutate();
        var osvServer = MockRestServiceServer.bindTo(osvBuilder).build();
        osvServer.expect(requestTo("https://osv.test/v1/querybatch")).andRespond(withSuccess());
        osvBuilder.build().get().uri("https://osv.test/v1/querybatch").retrieve().toBodilessEntity();

        assertThat(observed).isNotEmpty();
    }
}
