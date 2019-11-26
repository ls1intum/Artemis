package de.tum.in.www1.artemis.service.feature;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Feature {

    PROGRAMMING_EXERCISES(true);

    private boolean enabled;

    Feature(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Now, normally it is not a good practice to have setters for enums, but I think this is actually a rare case where
    // it makes sense to allow this.
    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /**
     * Get all features that are currently enabled on the system
     *
     * @return A list of enabled features
     */
    public static List<Feature> enabledFeatures() {
        return Arrays.stream(Feature.values()).filter(feature -> feature.enabled).collect(Collectors.toList());
    }
}
