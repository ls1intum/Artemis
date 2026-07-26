package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

class FeatureToggleServiceGocastConfigurationTest {

    private FeatureToggleService featureToggleService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
        LifecycleService lifecycleService = mock(LifecycleService.class);
        IMap<Feature, Boolean> featureMap = mock(IMap.class);
        Map<Feature, Boolean> values = new EnumMap<>(Feature.class);
        when(hazelcastInstance.getLifecycleService()).thenReturn(lifecycleService);
        when(lifecycleService.isRunning()).thenReturn(true);
        when(hazelcastInstance.<Feature, Boolean>getMap("features")).thenReturn(featureMap);
        when(featureMap.containsKey(any())).thenAnswer(invocation -> values.containsKey(invocation.getArgument(0)));
        when(featureMap.get(any())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        when(featureMap.entrySet()).thenAnswer(invocation -> values.entrySet());
        doAnswer(invocation -> values.put(invocation.getArgument(0), invocation.getArgument(1))).when(featureMap).put(any(), any());
        doAnswer(invocation -> {
            values.putAll(invocation.getArgument(0));
            return null;
        }).when(featureMap).putAll(any());

        ProfileService profileService = mock(ProfileService.class);
        RateLimitConfigurationService rateLimitConfigurationService = mock(RateLimitConfigurationService.class);
        featureToggleService = new FeatureToggleService(mock(WebsocketMessagingService.class), hazelcastInstance, profileService, rateLimitConfigurationService, false, "", "", "",
                "");
    }

    @Test
    void enableFeatureKeepsGocastDisabledWhenConfigurationIsIncomplete() {
        featureToggleService.enableFeature(Feature.Gocast);

        assertThat(featureToggleService.isFeatureEnabled(Feature.Gocast)).isFalse();
    }

    @Test
    void bulkUpdateKeepsGocastDisabledWhenConfigurationIsIncomplete() {
        featureToggleService.updateFeatureToggles(Map.of(Feature.Gocast, true, Feature.ProgrammingExercises, true));

        assertThat(featureToggleService.isFeatureEnabled(Feature.Gocast)).isFalse();
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isTrue();
    }
}
