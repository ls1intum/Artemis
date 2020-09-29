package de.tum.in.www1.artemis.service.feature;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class FeatureToggleService {

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    private final WebsocketMessagingService websocketMessagingService;

    private final List<Feature> enabledFeatures;

    private final List<Feature> disabledFeatures;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService, HazelcastInstance hazelcastInstance) {
        this.websocketMessagingService = websocketMessagingService;

        // The lists will automatically be distributed between all instances by Hazelcast.
        // If this instance is the first instance to boot up, both instances will be empty, otherwise at least one will have entries.
        enabledFeatures = hazelcastInstance.getList("enabled_features");
        disabledFeatures = hazelcastInstance.getList("disabled_features");

        // Features that are neither enabled nor disabled should be enabled by default
        // This ensures that all features are enabled once the system starts up
        for (Feature feature : Feature.values()) {
            if (!enabledFeatures.contains(feature) && !disabledFeatures.contains(feature)) {
                enabledFeatures.add(feature);
            }
        }
    }

    /**
     * Enables the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be enabled
     */
    public void enableFeature(Feature feature) {
        if (!enabledFeatures.contains(feature)) {
            enabledFeatures.add(feature);
        }
        disabledFeatures.remove(feature);

        websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures);
    }

    /**
     * Disable the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be disabled
     */
    public void disableFeature(Feature feature) {
        if (!disabledFeatures.contains(feature)) {
            disabledFeatures.add(feature);
        }
        enabledFeatures.remove(feature);

        websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures);
    }

    /**
     * Updates the given feature toggles and enables/disables the features based on the given map. Also notifies all clients
     * by sending a message via the websocket.
     *
     * @param features A map of features (feature -> shouldBeActivated)
     */
    public void updateFeatureToggles(final Map<Feature, Boolean> features) {
        features.forEach((feature, setEnabled) -> {
            if (setEnabled) {
                enabledFeatures.add(feature);
                disabledFeatures.remove(feature);
            }
            else {
                disabledFeatures.add(feature);
                enabledFeatures.remove(feature);
            }
        });

        websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures);
    }

    /**
     * Return whether a given feature is enabled
     *
     * @param feature the feature that should be checked
     * @return if the feature is enabled
     */
    public boolean isFeatureEnabled(Feature feature) {
        return enabledFeatures.contains(feature);
    }

    /**
     * Get all features that are currently enabled on the system
     *
     * @return A list of enabled features
     */
    public List<Feature> enabledFeatures() {
        return enabledFeatures;
    }

    /**
     * Get all features that are currently disabled on the system
     *
     * @return A list of disabled features
     */
    public List<Feature> disabledFeatures() {
        return disabledFeatures;
    }
}
