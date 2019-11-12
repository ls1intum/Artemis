package de.tum.in.www1.artemis.service.feature;

public enum Feature {
    PROGRAMMING_EXERCISES(true);

    private boolean enabled;

    Feature(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
