package de.tum.cit.aet.artemis.core.service.feature;

import static de.tum.cit.aet.artemis.core.web.CoreWebsocketTopics.FEATURE_TOGGLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class FeatureToggleInitializationTest {

    @ParameterizedTest
    @CsvSource({ "false,false,false", "true,false,false", "false,true,false", "true,true,true" })
    void combinedReconcileSeedRequiresBothPassesAndOtherSeedsRemainIndependent(boolean missing, boolean drift, boolean combined) {
        var provider = new LocalDataProviderService();
        var rateLimit = mock(RateLimitConfigurationService.class);
        when(rateLimit.isRateLimitingEnabled()).thenReturn(true);
        var profiles = mock(ProfileService.class);
        when(profiles.isDevActive()).thenReturn(true);
        var service = new FeatureToggleService(mock(WebsocketMessagingService.class), provider, profiles, rateLimit, true, missing, drift, true);
        ReflectionTestUtils.setField(service, "scienceEnabledOnStart", true);
        assertThat(service.isFeatureEnabled(Feature.GlobalSearchReconcile)).isEqualTo(combined);
        assertThat(service.isFeatureEnabled(Feature.GlobalSearchReconcileOrphan)).isTrue();
        assertThat(service.isFeatureEnabled(Feature.GlobalSearch)).isTrue();
        assertThat(service.isFeatureEnabled(Feature.Science)).isTrue();
        assertThat(service.isFeatureEnabled(Feature.RateLimit)).isTrue();
        assertThat(service.isFeatureEnabled(Feature.LectureContentProcessing)).isFalse();
        assertThat(service.isFeatureEnabled(Feature.Deimos)).isFalse();
    }

    @Test
    void existingRuntimeChoicesSurviveReinitializationAndLectureConfigCanEnableDevelopment() {
        var provider = new LocalDataProviderService();
        DistributedMap<Feature, Boolean> map = provider.getMap("features");
        for (Feature feature : Feature.values()) {
            map.put(feature, false);
        }
        map.put(Feature.LectureContentProcessing, true);
        var profiles = mock(ProfileService.class);
        when(profiles.isDevActive()).thenReturn(true);
        var rateLimit = mock(RateLimitConfigurationService.class);
        when(rateLimit.isRateLimitingEnabled()).thenReturn(true);
        var websocket = mock(WebsocketMessagingService.class);
        var service = new FeatureToggleService(websocket, provider, profiles, rateLimit, true, true, true, true);
        ReflectionTestUtils.setField(service, "lectureContentProcessingEnabledOnStart", true);
        assertThat(service.enabledFeatures()).containsExactly(Feature.LectureContentProcessing);
        assertThat(service.disabledFeatures()).contains(Feature.GlobalSearch, Feature.GlobalSearchReconcile, Feature.GlobalSearchReconcileOrphan, Feature.RateLimit);
        service.enableFeature(Feature.GlobalSearch);
        assertThat(service.isFeatureEnabled(Feature.GlobalSearch)).isTrue();
        service.disableFeature(Feature.LectureContentProcessing);
        assertThat(service.enabledFeatures()).containsExactly(Feature.GlobalSearch);
        service.updateFeatureToggles(Map.of(Feature.GlobalSearch, false, Feature.Science, true));
        assertThat(service.enabledFeatures()).containsExactly(Feature.Science);
        verify(websocket).sendMessage(eq(FEATURE_TOGGLES.at()), eq(List.of(Feature.Science)));
    }

    /**
     * Memiris was removed but keeps its enum position for the Redis key encoding. A value an older node stored for it is
     * dropped at startup, writes for it are ignored, and a value an older node writes later never makes it look enabled.
     */
    @Test
    void aRetiredFeatureIsRemovedAtStartupAndNeverReportedAsEnabled() {
        var provider = new LocalDataProviderService();
        DistributedMap<Feature, Boolean> map = provider.getMap("features");
        map.put(Feature.Memiris, true);
        var service = new FeatureToggleService(mock(WebsocketMessagingService.class), provider, mock(ProfileService.class), mock(RateLimitConfigurationService.class), false, false,
                false, false);

        assertThat(service.enabledFeatures()).doesNotContain(Feature.Memiris);
        assertThat(map.containsKey(Feature.Memiris)).isFalse();

        service.enableFeature(Feature.Memiris);
        service.disableFeature(Feature.Memiris);
        service.updateFeatureToggles(Map.of(Feature.Memiris, true, Feature.Science, true));
        assertThat(map.containsKey(Feature.Memiris)).isFalse();
        assertThat(service.isFeatureEnabled(Feature.Science)).isTrue();

        // An older node that still knows the feature can write it directly to the shared map
        map.put(Feature.Memiris, true);
        assertThat(service.isFeatureEnabled(Feature.Memiris)).isFalse();
        assertThat(service.enabledFeatures()).doesNotContain(Feature.Memiris);
        map.put(Feature.Memiris, false);
        assertThat(service.disabledFeatures()).doesNotContain(Feature.Memiris);
    }

    @Test
    void unavailableStoreAndFailedBroadcastDoNotLeakExceptionsOrInventChanges() {
        var provider = mock(DistributedDataProvider.class);
        var websocket = mock(WebsocketMessagingService.class);
        var service = new FeatureToggleService(websocket, provider, mock(ProfileService.class), mock(RateLimitConfigurationService.class), false, false, false, false);
        service.enableFeature(Feature.Science);
        service.disableFeature(Feature.Science);
        service.updateFeatureToggles(Map.of(Feature.Science, true));
        assertThat(service.enabledFeatures()).isEmpty();
        verifyNoInteractions(websocket);
        var local = new LocalDataProviderService();
        DistributedMap<Feature, Boolean> map = local.getMap("features");
        when(provider.isInstanceRunning()).thenReturn(true);
        when(provider.<Feature, Boolean>getMap("features")).thenReturn(map);
        doThrow(new IllegalStateException("websocket unavailable")).when(websocket).sendMessage(any(), any(Object.class));
        service.enableFeature(Feature.Science);
        assertThat(service.isFeatureEnabled(Feature.Science)).isTrue();
    }
}
