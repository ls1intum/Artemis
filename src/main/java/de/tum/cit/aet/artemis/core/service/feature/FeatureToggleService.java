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

    private Map<String, Boolean> features;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.websocketMessagingService = websocketMessagingService;
        this.hazelcastInstance = hazelcastInstance;
    }

    private Optional<Map<String, Boolean>> getFeatures() {
        try {
            if (isHazelcastRunning()) {
                return Optional.ofNullable(features);
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to get features in {} as Hazelcast instance is not active anymore.", FeatureToggleService.class.getSimpleName());
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

        for (CommunicationFeature feature : CommunicationFeature.getConfigurableFeatures()) {
            if (!features.containsKey(feature)) {
                features.put(feature.name(), true);
            }
        }

        for (Feature feature : Feature.values()) {
            if (!features.containsKey(feature) && feature != Feature.Science) {
                features.put(feature.name(), true);
            }
        }
        // init science feature from config
        if (!features.containsKey(Feature.Science)) {
            features.put(Feature.Science.name(), scienceEnabledOnStart);
        }
    }

    /**
     * Enables the given feature. Also notifies all clients by sending a message via the websocket.
     *
     * @param feature The feature that should be enabled
     */
    public void enableFeature(Feature feature) {
        getFeatures().ifPresent(features -> {
            features.put(feature.name(), true);
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
            features.put(feature.name(), false);
            sendUpdate();
        });
    }

    /**
     * Updates the given feature toggles and enables/disables the features based on the given map. Also notifies all clients
     * by sending a message via the websocket.
     *
     * @param updatedFeatures A map of features (feature -> shouldBeActivated)
     */
    public void updateFeatureToggles(final Map<String, Boolean> updatedFeatures) {
        getFeatures().ifPresent(features -> {
            features.putAll(updatedFeatures);
            sendUpdate();
        });
    }

    private void sendUpdate() {
        try {
            if (isHazelcastRunning()) {
                websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures());
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to send features update in {} as Hazelcast instance is not active anymore.", FeatureToggleService.class.getSimpleName());
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
            if (isHazelcastRunning()) {
                Boolean isEnabled = features.get(feature.name());
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
    public List<AbstractFeature> enabledFeatures() {
        try {
            if (isHazelcastRunning()) {
                return features.entrySet().stream().filter(entry -> Boolean.TRUE.equals(entry.getValue())).map(entry -> {
                    AbstractFeature feature = getFeatureByName(entry.getKey());
                    return feature != null ? feature : null;
                }).filter(feature -> feature != null).toList();
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to retrieve enabled features update in FeatureToggleService as Hazelcast instance is not active anymore.");
        }
        return List.of();
    }

    /**
     * Get all features that are currently disabled on the system
     *
     * @return A list of disabled features
     */
    public List<AbstractFeature> disabledFeatures() {
        try {
            if (isHazelcastRunning()) {
                return features.entrySet().stream().filter(entry -> Boolean.FALSE.equals(entry.getValue())).map(entry -> {
                    AbstractFeature feature = getFeatureByName(entry.getKey());
                    return feature != null ? feature : null;
                }).filter(feature -> feature != null).toList();
            }
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Failed to retrieve disabled features update in FeatureToggleService as Hazelcast instance is not active anymore.");
        }
        return List.of();
    }

    private boolean isHazelcastRunning() {
        return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
    }

    private AbstractFeature getFeatureByName(String name) {
        for (Feature feature : Feature.values()) {
            if (feature.name().equals(name)) {
                return feature;
            }
        }
        for (CommunicationFeature feature : CommunicationFeature.getConfigurableFeatures()) {
            if (feature.name().equals(name)) {
                return feature;
            }
        }
        return null;
    }
}
