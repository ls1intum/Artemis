package de.tum.cit.aet.artemis.core.service.feature;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

@Profile(PROFILE_CORE)
@Service
public class FeatureToggleService {

    private static final Logger log = LoggerFactory.getLogger(FeatureToggleService.class);

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    @Value("${artemis.science.event-logging.enable:false}")
    private boolean scienceEnabledOnStart;

    private final WebsocketMessagingService websocketMessagingService;

    private final RedissonClient redissonClient;

    private Map<Feature, Boolean> features;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService, RedissonClient redissonClient) {
        this.websocketMessagingService = websocketMessagingService;
        this.redissonClient = redissonClient;
    }

    private Optional<Map<Feature, Boolean>> getFeatures() {
        try {
            return Optional.ofNullable(features);
        }
        catch (RedisConnectionException e) {
            log.error("Failed to get features in {} due to Redis connection exception", FeatureToggleService.class.getSimpleName());
        }
        return Optional.empty();
    }

    /**
     * Initialize relevant data from Redis
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        features = redissonClient.getMap("features");

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
            websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures());
        }
        catch (RedisConnectionException e) {
            log.error("Failed to send features update in {} due to Redis connection exception.", FeatureToggleService.class.getSimpleName());
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
            Boolean isEnabled = features.get(feature);
            return Boolean.TRUE.equals(isEnabled);
        }
        catch (RedisConnectionException e) {
            log.error("Failed to check if feature is enabled in FeatureToggleService due to Redis connection exception.");
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
            return features.entrySet().stream().filter(feature -> Boolean.TRUE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
        }
        catch (RedisConnectionException e) {
            log.error("Failed to retrieve enabled features update in FeatureToggleService due to Redis connection exception.");
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
            return features.entrySet().stream().filter(feature -> Boolean.FALSE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
        }
        catch (RedisConnectionException e) {
            log.error("Failed to retrieve disabled features update in FeatureToggleService due to Redis connection exception.");
        }
        return List.of();
    }
}
