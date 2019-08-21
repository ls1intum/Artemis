package de.tum.in.www1.artemis.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GuidedTourSettingsTest {

    @Test
    void testGuidedTourKey() {
        GuidedTourSettings guidedTourSettings = new GuidedTourSettings();
        guidedTourSettings = guidedTourSettings.guidedTourKey("guided_tour_key").guidedTourStep(5);

        assertEquals(5, guidedTourSettings.getGuidedTourStep());
        assertEquals("guided_tour_key", guidedTourSettings.getGuidedTourKey());
    }

    @Test
    void testGuidedTourStep() {
        GuidedTourSettings guidedTourSettings = new GuidedTourSettings();
        guidedTourSettings = guidedTourSettings.guidedTourStep(5).guidedTourKey("guided_tour_key");

        assertEquals(5, guidedTourSettings.getGuidedTourStep());
        assertEquals("guided_tour_key", guidedTourSettings.getGuidedTourKey());
    }
}
