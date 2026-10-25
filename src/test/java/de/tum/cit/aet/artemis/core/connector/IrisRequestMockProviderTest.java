package de.tum.cit.aet.artemis.core.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class IrisRequestMockProviderTest {

    @Test
    void configuresLectureWebhookExpectationsBeforeAcceptingLateRequests() throws Exception {
        var restTemplate = new RestTemplate();
        var provider = new IrisRequestMockProvider(restTemplate, new RestTemplate());
        String webhooksUrl = "https://pyris.test/api/v1/webhooks";
        ReflectionTestUtils.setField(provider, "webhooksApiURL", URI.create(webhooksUrl).toURL());
        ReflectionTestUtils.setField(provider, "mapper", new ObjectMapper());
        var previousServer = MockRestServiceServer.bindTo(restTemplate).build();
        previousServer.expect(requestTo(webhooksUrl + "/lectures/ingest")).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.ACCEPTED));
        var configuredRequests = new AtomicInteger();

        try (var executor = Executors.newSingleThreadExecutor()) {
            provider.enableMockingOfRequests(() -> {
                provider.mockIngestionWebhookRunResponse(dto -> configuredRequests.incrementAndGet(), ExpectedCount.once());
                // A request already queued by the previous test arrives before the remaining expectations are registered.
                CompletableFuture.runAsync(() -> restTemplate.postForEntity(webhooksUrl + "/lectures/ingest", "{}", Void.class), executor).join();
                provider.mockDeletionWebhookRunResponse(dto -> configuredRequests.incrementAndGet(), ExpectedCount.once());
                provider.mockLectureUnitMetadataWebhookRunResponse(dto -> configuredRequests.incrementAndGet(), ExpectedCount.once());
                provider.mockLectureUnitVisibilityWebhookRunResponse(dto -> configuredRequests.incrementAndGet(), ExpectedCount.once());
            });

            previousServer.verify();
            assertThat(configuredRequests).hasValue(0);
            for (String webhook : List.of("ingest", "delete", "metadata", "visibility")) {
                assertThat(restTemplate.postForEntity(webhooksUrl + "/lectures/" + webhook, "{}", Void.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            }
            assertThat(configuredRequests).hasValue(4);
            provider.verify();
        }
        finally {
            provider.reset();
        }
    }
}
