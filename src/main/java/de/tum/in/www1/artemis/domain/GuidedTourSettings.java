package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import com.google.gson.Gson;

public class GuidedTourSettings implements Serializable {

    private boolean showCourseOverviewTour = false;

    private boolean showNavigationTour = false;

    private boolean showProgrammingExerciseTour = false;

    private boolean showQuizExerciseTour = false;

    private boolean showModelingExerciseTour = false;

    private boolean showTextExerciseTour = false;

    public GuidedTourSettings() {
    }

    public GuidedTourSettings(boolean showCourseOverviewTour, boolean showNavigationTour, boolean showProgrammingExerciseTour, boolean showQuizExerciseTour,
            boolean showModelingExerciseTour, boolean showTextExerciseTour) {
        this.showCourseOverviewTour = showCourseOverviewTour;
        this.showNavigationTour = showNavigationTour;
        this.showProgrammingExerciseTour = showProgrammingExerciseTour;
        this.showQuizExerciseTour = showQuizExerciseTour;
        this.showModelingExerciseTour = showModelingExerciseTour;
        this.showTextExerciseTour = showTextExerciseTour;
    }

    public boolean isShowCourseOverviewTour() {
        return showCourseOverviewTour;
    }

    public void setShowCourseOverviewTour(boolean showCourseOverviewTour) {
        this.showCourseOverviewTour = showCourseOverviewTour;
    }

    public boolean isShowNavigationTour() {
        return showNavigationTour;
    }

    public void setShowNavigationTour(boolean showNavigationTour) {
        this.showNavigationTour = showNavigationTour;
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
