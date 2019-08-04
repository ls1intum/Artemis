package de.tum.in.www1.artemis.domain;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GuidedTourSettingsTest {

    @Test
    public void createFromJson() {
        String guidedTourSettingsJson = "{\n" + "\t\"showCourseOverviewTour\" : true,\n" + "\t\"showNavigationTour\" : false,\n" + "\t\"showProgrammingExerciseTour\" : true,\n"
                + "\t\"showQuizExerciseTour\" : false,\n" + "\t\"showModelingExerciseTour\" : true,\n" + "\t\"showTextExerciseTour\" : false\n" + "}";

        GuidedTourSettings guidedTourSettings = GuidedTourSettings.createFromJson(guidedTourSettingsJson);

        Assertions.assertTrue(guidedTourSettings.isShowCourseOverviewTour(), "showCourseOverviewTour must be true");
        Assertions.assertTrue(guidedTourSettings.isShowProgrammingExerciseTour(), "showProgrammingExerciseTour must be true");
        Assertions.assertTrue(guidedTourSettings.isShowModelingExerciseTour(), "showModelingExerciseTour must be true");

        Assertions.assertFalse(guidedTourSettings.isShowNavigationTour(), "showNavigationTour must be false");
        Assertions.assertFalse(guidedTourSettings.isShowQuizExerciseTour(), "showQuizExerciseTour must be false");
        Assertions.assertFalse(guidedTourSettings.isShowTextExerciseTour(), "showTextExerciseTour must be false");
    }
}
