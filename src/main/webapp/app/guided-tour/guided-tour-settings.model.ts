export class GuidedTourSettings {
    public showCourseOverviewTour: boolean;
    public showNavigationTour: boolean;
    public showProgrammingExerciseTour: boolean;
    public showQuizExerciseTour: boolean;
    public showModelingExerciseTour: boolean;
    public showTextExerciseTour: boolean;

    constructor(
        showCourseOverviewTour: boolean,
        showNavigationTour: boolean,
        showProgrammingExerciseTour: boolean,
        showQuizExerciseTour: boolean,
        showModelingExerciseTour: boolean,
        showTextExerciseTour: boolean,
    ) {
        this.showCourseOverviewTour = showCourseOverviewTour;
        this.showNavigationTour = showNavigationTour;
        this.showProgrammingExerciseTour = showProgrammingExerciseTour;
        this.showQuizExerciseTour = showQuizExerciseTour;
        this.showModelingExerciseTour = showModelingExerciseTour;
        this.showTextExerciseTour = showTextExerciseTour;
    }
}
