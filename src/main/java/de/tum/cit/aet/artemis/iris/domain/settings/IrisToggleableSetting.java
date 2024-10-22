package de.tum.cit.aet.artemis.iris.domain.settings;

/**
 * Represents an Iris setting that can be toggled on or off.
 */
public interface IrisToggleableSetting {

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
