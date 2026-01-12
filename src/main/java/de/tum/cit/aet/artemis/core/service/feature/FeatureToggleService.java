package de.tum.cit.aet.artemis.core.service.feature;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FeatureToggleService {

    private static final Logger log = LoggerFactory.getLogger(FeatureToggleService.class);

    private static final String TOPIC_FEATURE_TOGGLES = "/topic/management/feature-toggles";

    @Value("${artemis.science.event-logging.enable:false}")
    private boolean scienceEnabledOnStart;

    private final WebsocketMessagingService websocketMessagingService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProfileService profileService;

    public FeatureToggleService(WebsocketMessagingService websocketMessagingService, DistributedDataAccessService distributedDataAccessService, ProfileService profileService) {
        this.websocketMessagingService = websocketMessagingService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.profileService = profileService;
    }

    private Optional<DistributedMap<Feature, Boolean>> getFeatures() {
        if (distributedDataAccessService.isInstanceRunning()) {
            return Optional.ofNullable(distributedDataAccessService.getDistributedFeatures());
        }
        return Optional.empty();
    }

    /**
     * Lazy init: Retrieves the Hazelcast map that stores features
     * If the map is not initialized, it initializes it.
     *
     * @return The map of features
     */
    private Map<Feature, Boolean> getFeatauresMap() {
        if (distributedDataAccessService.getFeatures() == null) {
            initFeatures();
        }
        return distributedDataAccessService.getFeatures();
    }

    /**
     * Initialize relevant data from hazelcast
     */
    private void initFeatures() {
        DistributedMap<Feature, Boolean> features = distributedDataAccessService.getDistributedFeatures();

        // Features that are neither enabled nor disabled should be enabled by default
        // This ensures that all features (except the Science API, TutorSuggestions, AtlasML, Memiris and AtlasAgent) are enabled once the system starts up
        for (Feature feature : Feature.values()) {
            if (!features.containsKey(feature) && feature != Feature.Science && feature != Feature.TutorSuggestions && feature != Feature.AtlasML && feature != Feature.Memiris
                    && feature != Feature.AtlasAgent) {
                features.put(feature, true);
            }
        }
        // init science feature from config
        if (!features.containsKey(Feature.Science)) {
            features.put(Feature.Science, scienceEnabledOnStart);
        }

        if (!features.containsKey(Feature.AtlasAgent)) {
            features.put(Feature.AtlasAgent, false);
        }

        if (!features.containsKey(Feature.TutorSuggestions)) {
            features.put(Feature.TutorSuggestions, false);
        }

        if (!features.containsKey(Feature.AtlasML)) {
            features.put(Feature.AtlasML, false);
        }

        if (!features.containsKey(Feature.Memiris)) {
            features.put(Feature.Memiris, false);
        }

        // Disable LectureContentProcessing in dev profile to avoid issues with local file system access
        if (profileService.isDevActive()) {
            features.put(Feature.LectureContentProcessing, false);
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
        if (distributedDataAccessService.isInstanceRunning()) {
            websocketMessagingService.sendMessage(TOPIC_FEATURE_TOGGLES, enabledFeatures());
        }
    }

    /**
     * Return whether a given feature is enabled
     *
     * @param feature the feature that should be checked
     * @return if the feature is enabled
     */
    public boolean isFeatureEnabled(Feature feature) {
        if (distributedDataAccessService.isInstanceRunning()) {
            Boolean isEnabled = getFeatauresMap().get(feature);
            return Boolean.TRUE.equals(isEnabled);
        }
        return false;
    }

    /**
     * Get all features that are currently enabled on the system
     *
     * @return A list of enabled features
     */
    public List<Feature> enabledFeatures() {
        if (distributedDataAccessService.isInstanceRunning()) {
            return getFeatauresMap().entrySet().stream().filter(feature -> Boolean.TRUE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
        }
        return List.of();
    }

    /**
     * Get all features that are currently disabled on the system
     *
     * @return A list of disabled features
     */
    public List<Feature> disabledFeatures() {
        if (distributedDataAccessService.isInstanceRunning()) {
            return getFeatauresMap().entrySet().stream().filter(feature -> Boolean.FALSE.equals(feature.getValue())).map(Map.Entry::getKey).toList();
        }
        return List.of();
    }
}
