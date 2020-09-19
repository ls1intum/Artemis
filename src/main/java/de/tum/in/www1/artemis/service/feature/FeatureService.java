package de.tum.in.www1.artemis.service.feature;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class FeatureService {

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    private final WebsocketMessagingService websocketMessagingService;

    private List<Feature> enabledFeatures;

    private List<Feature> disabledFeatures;

    public FeatureService(WebsocketMessagingService websocketMessagingService, HazelcastInstance hazelcastInstance) {
        this.websocketMessagingService = websocketMessagingService;

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
}
