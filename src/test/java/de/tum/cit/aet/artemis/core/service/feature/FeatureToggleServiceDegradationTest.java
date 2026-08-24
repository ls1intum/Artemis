package de.tum.cit.aet.artemis.core.service.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/**
 * Feature toggles are read on request paths, so an unreachable distributed store must degrade rather than fail the
 * request. A node can be in that state while it is starting or reconnecting.
 *
 * <p>
 * Each backend throws its own unavailability type, so the service guards on a runtime exception. These tests pin down that
 * the guard actually holds for both shapes it can take: the provider reporting itself as not running, and the provider
 * throwing on access.
 */
class FeatureToggleServiceDegradationTest {

    /**
     * @param provider the provider the service should use
     * @return a service wired with mocked collaborators
     */
    private FeatureToggleService serviceWith(DistributedDataProvider provider) {
        return new FeatureToggleService(mock(WebsocketMessagingService.class), provider, mock(ProfileService.class), mock(RateLimitConfigurationService.class), false);
    }

    @Test
    void shouldReportFeatureDisabledWhenProviderIsNotRunning() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(provider.isInstanceRunning()).thenReturn(false);
        FeatureToggleService service = serviceWith(provider);

        assertThat(service.isFeatureEnabled(Feature.ProgrammingExercises)).isFalse();
        assertThat(service.enabledFeatures()).isEmpty();
        assertThat(service.disabledFeatures()).isEmpty();
    }

    @Test
    void shouldReportFeatureDisabledWhenProviderThrowsOnAccess() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(provider.isInstanceRunning()).thenReturn(true);
        when(provider.getMap(anyString())).thenThrow(new IllegalStateException("cluster unreachable"));
        FeatureToggleService service = serviceWith(provider);

        assertThat(service.isFeatureEnabled(Feature.ProgrammingExercises)).isFalse();
        assertThat(service.enabledFeatures()).isEmpty();
        assertThat(service.disabledFeatures()).isEmpty();
    }

    /**
     * A write attempted while the store is unreachable must not throw either; the caller is an admin REST request.
     */
    @Test
    void shouldNotThrowWhenUpdatingTogglesWhileUnavailable() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(provider.isInstanceRunning()).thenReturn(false);
        FeatureToggleService service = serviceWith(provider);

        service.updateFeatureToggles(Map.of(Feature.ProgrammingExercises, false));

        assertThat(service.isFeatureEnabled(Feature.ProgrammingExercises)).isFalse();
    }
}
