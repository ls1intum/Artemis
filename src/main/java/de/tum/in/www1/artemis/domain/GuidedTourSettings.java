package de.tum.in.www1.artemis.domain;

import com.google.gson.Gson;

public class GuidedTourSettings {

    private boolean showCourseOverviewTour = false;

    private boolean showNavigationTour = false;

    private boolean showProgrammingExerciseTour = false;

    private boolean showQuizExerciseTour = false;

    private boolean showModelingExerciseTour = false;

    private boolean showTextExerciseTour = false;

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

    @Override
    public String toString() {
        return "GuidedTourSettings[" + "showCourseOverviewTour=" + showCourseOverviewTour + ", showNavigationTour=" + showNavigationTour + ", showProgrammingExerciseTour="
                + showProgrammingExerciseTour + ", showQuizExerciseTour=" + showQuizExerciseTour + ", showModelingExerciseTour=" + showModelingExerciseTour
                + ", showTextExerciseTour=" + showTextExerciseTour + "]";
    }
}
