package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuidedTourSettingTest {

    @Test
    void testGuidedTourKey() {
        GuidedTourSetting guidedTourSetting = new GuidedTourSetting();
        guidedTourSetting = guidedTourSetting.guidedTourKey("guided_tour_key").guidedTourStep(5);

        assertThat(guidedTourSetting.getGuidedTourStep()).isEqualTo(5);
        assertThat(guidedTourSetting.getGuidedTourKey()).isEqualTo("guided_tour_key");
    }

    @Test
    void testGuidedTourStep() {
        GuidedTourSetting guidedTourSetting = new GuidedTourSetting();
        guidedTourSetting = guidedTourSetting.guidedTourStep(5).guidedTourKey("guided_tour_key");

        assertThat(guidedTourSetting.getGuidedTourStep()).isEqualTo(5);
        assertThat(guidedTourSetting.getGuidedTourKey()).isEqualTo("guided_tour_key");
    }
}
