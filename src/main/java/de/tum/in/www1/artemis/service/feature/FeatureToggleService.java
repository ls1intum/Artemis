package de.tum.in.www1.artemis.service.feature;

import java.util.Map;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class FeatureToggleService {

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    private final WebsocketMessagingService websocketMessagingService;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
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
                feature.enable();
            }
            else {
                feature.disable();
            }
        });

        websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, Feature.enabledFeatures());
    }
}
