package de.tum.in.www1.artemis.service.feature;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;

@Service
public class FeatureToggleService {

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    private final WebsocketMessagingService websocketMessagingService;

    private final ExamMonitoringScheduleService examMonitoringScheduleService;

    private final Map<Feature, Boolean> features;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService, ExamMonitoringScheduleService examMonitoringScheduleService,
            HazelcastInstance hazelcastInstance) {
        this.websocketMessagingService = websocketMessagingService;
        this.examMonitoringScheduleService = examMonitoringScheduleService;

        // The map will automatically be distributed between all instances by Hazelcast.
        features = hazelcastInstance.getMap("features");

        // Features that are neither enabled nor disabled should be enabled by default
        // This ensures that all features are enabled once the system starts up
        for (Feature feature : Feature.values()) {
            if (!features.containsKey(feature)) {
                features.put(feature, true);
            }
        }
    }

    /**
     * Enables the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be enabled
     */
    public void enableFeature(Feature feature) {
        features.put(feature, true);
        sendUpdate();
    }

    /**
     * Disable the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be disabled
     */
    public void disableFeature(Feature feature) {
        if (feature == Feature.ExamLiveStatistics) {
            // We want to clear all the data, but keep the settings.
            examMonitoringScheduleService.clearAllExamMonitoringData();
        }
        features.put(feature, false);
        sendUpdate();
    }

    /**
     * Updates the given feature toggles and enables/disables the features based on the given map. Also notifies all clients
     * by sending a message via the websocket.
     *
     * @param features A map of features (feature -> shouldBeActivated)
     */
    public void updateFeatureToggles(final Map<Feature, Boolean> features) {
        this.features.putAll(features);
        sendUpdate();
    }

    private void sendUpdate() {
        websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures());
    }

    /**
     * Return whether a given feature is enabled
     *
     * @param feature the feature that should be checked
     * @return if the feature is enabled
     */
    public boolean isFeatureEnabled(Feature feature) {
        Boolean isEnabled = features.get(feature);
        return Boolean.TRUE.equals(isEnabled);
    }

    /**
     * Get all features that are currently enabled on the system
     *
     * @return A list of enabled features
     */
    public List<Feature> enabledFeatures() {
        return features.entrySet().stream().filter(feature -> Boolean.TRUE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
    }

    /**
     * Get all features that are currently disabled on the system
     *
     * @return A list of disabled features
     */
    public List<Feature> disabledFeatures() {
        return features.entrySet().stream().filter(feature -> Boolean.FALSE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
    }
}
