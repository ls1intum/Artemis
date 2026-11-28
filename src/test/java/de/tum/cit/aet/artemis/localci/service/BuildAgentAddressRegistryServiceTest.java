package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;

class BuildAgentAddressRegistryServiceTest {

    private final DistributedDataAccessService distributedDataAccessService = mock(DistributedDataAccessService.class);

    private final Map<String, BuildAgentAddressInfo> reportedAddresses = new HashMap<>();

    private BuildAgentAddressRegistryService service;

    @BeforeEach
    void setUp() {
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        when(distributedDataAccessService.getBuildAgentReportedAddressMap()).thenAnswer(_ -> Map.copyOf(reportedAddresses));
        service = new BuildAgentAddressRegistryService(distributedDataAccessService);
    }

    @Test
    void bindsReportedAddressesAndRecognisesEquivalentAddresses() {
        reportedAddresses.put("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("::1", "10.0.0.5"), ZonedDateTime.now(), true));
        service.refreshRegisteredAddresses();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "0:0:0:0:0:0:0:1")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "::ffff:10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.8")).isFalse();
        assertThat(service.isRegisteredBuildAgentAddress("10.0.0.5")).isTrue();
        assertThat(service.isRegisteredBuildAgentAddress("10.0.0.8")).isFalse();
    }

    @Test
    void refreshesOnAMissAndRemovesExpiredReports() {
        reportedAddresses.put("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("10.0.0.5"), ZonedDateTime.now(), true));
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        reportedAddresses.clear();
        service.refreshRegisteredAddresses();
        assertThat(service.isRegisteredBuildAgentAddress("10.0.0.5")).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.8")).isTrue();
    }

    @Test
    void refusesAMissWhenTheProviderIsUnavailable() {
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(false);
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isFalse();
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        when(distributedDataAccessService.getBuildAgentReportedAddressMap()).thenThrow(new IllegalStateException("unavailable"));
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isFalse();
    }

    /**
     * The race the coordinated refresh exists for. An agent that has just published itself is briefly absent from the
     * snapshot, and absence is what grants the not-observable exemption, so a request arriving in that window must not
     * be exempted just because another request happens to be doing the reconcile that would have registered the agent.
     * <p>
     * Under a compare-and-set debounce the loser did not wait: it decided on the snapshot the winner was replacing and
     * was admitted from an address the agent was never observed at. Both requests here present the wrong address, so
     * both must be refused, and the assertion fails against that earlier implementation.
     */
    @Test
    void shouldNotExemptAConcurrentRequestWhileAnotherIsRefreshing() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();

        when(distributedDataAccessService.getBuildAgentReportedAddressMap()).thenAnswer(_ -> {
            if (providerCalls.incrementAndGet() == 1) {
                providerEntered.countDown();
                assertThat(releaseProvider.await(10, TimeUnit.SECONDS)).as("the test must release the provider").isTrue();
            }
            return Map.of("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("10.0.0.5"), ZonedDateTime.now(), true));
        });

        // Neither request comes from an address agent-1 was observed at, and the snapshot starts empty, so under the
        // previous implementation whichever request did not win the debounce would have taken the exemption.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> service.isRegisteredAddressOfAgent("agent-1", "10.0.0.99"));
            assertThat(providerEntered.await(10, TimeUnit.SECONDS)).as("the first request must reach the provider").isTrue();
            Future<Boolean> second = executor.submit(() -> service.isRegisteredAddressOfAgent("agent-1", "10.0.0.99"));
            // Give the second request time to get as far as it is going to get while the first still holds the refresh.
            Thread.sleep(200);
            releaseProvider.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).as("the refreshing request must be refused, the address is not registered").isFalse();
            assertThat(second.get(20, TimeUnit.SECONDS)).as("the waiting request must be refused too, not exempted on the pre-refresh snapshot").isFalse();
        }
        finally {
            executor.shutdownNow();
        }
    }

}
