package de.tum.cit.aet.artemis.core.service.feature;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

@Profile(PROFILE_CORE)
@Service
public class FeatureToggleService {

    private static final Logger log = LoggerFactory.getLogger(FeatureToggleService.class);

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    @Value("${artemis.science.event-logging.enable:false}")
    private boolean scienceEnabledOnStart;

    private final WebsocketMessagingService websocketMessagingService;

    private final HazelcastInstance hazelcastInstance;

    private Map<Feature, Boolean> features;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.websocketMessagingService = websocketMessagingService;
        this.hazelcastInstance = hazelcastInstance;
    }

    private Optional<Map<Feature, Boolean>> getFeatures() {
        try {
            if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
                return Optional.ofNullable(features);
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to get features in FeatureToggleService as Hazelcast instance is not active any more.");
        }
        return Optional.empty();
    }

    /**
     * Initialize relevant data from hazelcast
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // The map will automatically be distributed between all instances by Hazelcast.
        features = hazelcastInstance.getMap("features");

        // Features that are neither enabled nor disabled should be enabled by default
        // This ensures that all features (except the Science API) are enabled once the system starts up
        for (Feature feature : Feature.values()) {
            if (!features.containsKey(feature) && feature != Feature.Science) {
                features.put(feature, true);
            }
        }
        // init science feature from config
        if (!features.containsKey(Feature.Science)) {
            features.put(Feature.Science, scienceEnabledOnStart);
        }
    }

    /**
     * Enables the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be enabled
     */
    public void enableFeature(Feature feature) {
        getFeatures().ifPresent(features -> {
            features.put(feature, true);
            sendUpdate();
        });
    }

    /**
     * Disable the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be disabled
     */
    public void disableFeature(Feature feature) {
        getFeatures().ifPresent(features -> {
            features.put(feature, false);
            sendUpdate();
        });
    }

    /**
     * Updates the given feature toggles and enables/disables the features based on the given map. Also notifies all clients
     * by sending a message via the websocket.
     *
     * @param updatedFeatures A map of features (feature -> shouldBeActivated)
     */
    public void updateFeatureToggles(final Map<Feature, Boolean> updatedFeatures) {
        getFeatures().ifPresent(features -> {
            features.putAll(updatedFeatures);
            sendUpdate();
        });
    }

    private void sendUpdate() {
        try {
            if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
                websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures());
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to send features update in FeatureToggleService as Hazelcast instance is not active any more.");
        }
    }

    /**
     * Return whether a given feature is enabled
     *
     * @param feature the feature that should be checked
     * @return if the feature is enabled
     */
    public boolean isFeatureEnabled(Feature feature) {
        try {
            if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
                Boolean isEnabled = features.get(feature);
                return Boolean.TRUE.equals(isEnabled);
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to check if feature is enabled in FeatureToggleService as Hazelcast instance is not active any more.");
        }
        return false;
    }

    /**
     * Get all features that are currently enabled on the system
     *
     * @return A list of enabled features
     */
    public List<Feature> enabledFeatures() {
        try {
            if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
                return features.entrySet().stream().filter(feature -> Boolean.TRUE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to retrieve enabled features update in FeatureToggleService as Hazelcast instance is not active any more.");
        }
        return List.of();
    }

    /**
     * Get all features that are currently disabled on the system
     *
     * @return A list of disabled features
     */
    public List<Feature> disabledFeatures() {
        try {
            if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
                return features.entrySet().stream().filter(feature -> Boolean.FALSE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to retrieve disabled features update in FeatureToggleService as Hazelcast instance is not active any more.");
        }
        return List.of();
    }
}
