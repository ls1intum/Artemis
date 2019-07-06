package de.tum.in.www1.artemis.domain;

import static org.junit.Assert.*;

import org.junit.Test;

public class GuidedTourSettingsTest {

    @Test
    public void createFromJson() {
        String guidedTourSettingsJson = "{\n" + "\t\"showCourseOverviewTour\" : true,\n" + "\t\"showNavigationTour\" : false,\n" + "\t\"showProgrammingExerciseTour\" : true,\n"
                + "\t\"showQuizExerciseTour\" : false,\n" + "\t\"showModelingExerciseTour\" : true,\n" + "\t\"showTextExerciseTour\" : false\n" + "}";

        GuidedTourSettings guidedTourSettings = GuidedTourSettings.createFromJson(guidedTourSettingsJson);

        assertTrue("showCourseOverviewTour must be true", guidedTourSettings.isShowCourseOverviewTour());
        assertTrue("showProgrammingExerciseTour must be true", guidedTourSettings.isShowProgrammingExerciseTour());
        assertTrue("showModelingExerciseTour must be true", guidedTourSettings.isShowModelingExerciseTour());

        assertFalse("showNavigationTour must be false", guidedTourSettings.isShowNavigationTour());
        assertFalse("showQuizExerciseTour must be false", guidedTourSettings.isShowQuizExerciseTour());
        assertFalse("showTextExerciseTour must be false", guidedTourSettings.isShowTextExerciseTour());
    }
}
