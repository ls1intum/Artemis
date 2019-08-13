package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import com.google.gson.Gson;

/**
 * GuidedTourSettings
 */
public class GuidedTourSettings implements Serializable {

    private boolean showCourseOverviewTour;

    private boolean showProgrammingExerciseTour;

    private boolean showQuizExerciseTour;

    private boolean showModelingExerciseTour;

    private boolean showTextExerciseTour;

    public GuidedTourSettings() {
        this.showCourseOverviewTour = false;
        this.showProgrammingExerciseTour = false;
        this.showQuizExerciseTour = false;
        this.showModelingExerciseTour = false;
        this.showTextExerciseTour = false;
    }

    public boolean isShowCourseOverviewTour() {
        return showCourseOverviewTour;
    }

    public void setShowCourseOverviewTour(boolean showCourseOverviewTour) {
        this.showCourseOverviewTour = showCourseOverviewTour;
    }

    public boolean isShowProgrammingExerciseTour() {
        return showProgrammingExerciseTour;
    }

    public void setShowProgrammingExerciseTour(boolean showProgrammingExerciseTour) {
        this.showProgrammingExerciseTour = showProgrammingExerciseTour;
    }

    public boolean isShowQuizExerciseTour() {
        return showQuizExerciseTour;
    }

    public void setShowQuizExerciseTour(boolean showQuizExerciseTour) {
        this.showQuizExerciseTour = showQuizExerciseTour;
    }

    public boolean isShowModelingExerciseTour() {
        return showModelingExerciseTour;
    }

    public void setShowModelingExerciseTour(boolean showModelingExerciseTour) {
        this.showModelingExerciseTour = showModelingExerciseTour;
    }

    public boolean isShowTextExerciseTour() {
        return showTextExerciseTour;
    }

    public void setShowTextExerciseTour(boolean showTextExerciseTour) {
        this.showTextExerciseTour = showTextExerciseTour;
    }

    public static GuidedTourSettings createFromJson(String guidedTourSettingsJson) {
        Gson gson = new Gson();
        return gson.fromJson(guidedTourSettingsJson, GuidedTourSettings.class);
    }

    public static GuidedTourSettings defaultSettings() {
        return new GuidedTourSettings();
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public String toString() {
        return this.toJson();
    }
}
