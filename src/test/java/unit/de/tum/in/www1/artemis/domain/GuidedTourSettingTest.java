package de.tum.in.www1.artemis.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuidedTourSettingTest {

    @Test
    void testGuidedTourKey() {
        GuidedTourSetting guidedTourSetting = new GuidedTourSetting();
        guidedTourSetting = guidedTourSetting.guidedTourKey("guided_tour_key").guidedTourStep(5);

        assertEquals(5, guidedTourSetting.getGuidedTourStep());
        assertEquals("guided_tour_key", guidedTourSetting.getGuidedTourKey());
    }

    @Test
    void testGuidedTourStep() {
        GuidedTourSetting guidedTourSetting = new GuidedTourSetting();
        guidedTourSetting = guidedTourSetting.guidedTourStep(5).guidedTourKey("guided_tour_key");

        assertEquals(5, guidedTourSetting.getGuidedTourStep());
        assertEquals("guided_tour_key", guidedTourSetting.getGuidedTourKey());
    }
}
