package de.tum.cit.aet.artemis.sharing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class SharingRestClientConfigurationTest {

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

        RestClient.Builder sharingBuilder = new SharingRestClientConfiguration().sharingRestClient(RestClient.builder().observationRegistry(registry)).mutate();
        var sharingServer = MockRestServiceServer.bindTo(sharingBuilder).build();
        sharingServer.expect(requestTo("https://sharing.test/api/pluginIF/v0.1/reInitialize?apiKey=secret-key")).andRespond(withSuccess());
        sharingBuilder.build().get().uri("https://sharing.test/api/pluginIF/v0.1/reInitialize?apiKey=secret-key").retrieve().toBodilessEntity();
        sharingServer.verify();

        assertThat(observed).isEmpty();

        // Verify that the same registry records requests from a client with observations enabled.
        RestClient.Builder controlBuilder = RestClient.builder().observationRegistry(registry);
        var controlServer = MockRestServiceServer.bindTo(controlBuilder).build();
        controlServer.expect(requestTo("https://control.test/request")).andRespond(withSuccess());
        controlBuilder.build().get().uri("https://control.test/request").retrieve().toBodilessEntity();

        assertThat(observed).isNotEmpty();
    }
}
