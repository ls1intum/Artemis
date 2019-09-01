import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Orientation } from 'app/guided-tour/guided-tour.constants';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const courseExerciseOverviewTour: GuidedTour = {
    settingsKey: 'course_exercise_overview_tour',
    steps: [
        new TextTourStep({
            selector: '.tab-item.exercises',
            headlineTranslateKey: 'tour.courseExerciseOverview.exercises.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exercises.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            action: () => {
                clickOnElement('.tab-item.exercises');
            },
            closeAction: () => {
                clickOnElement('.tab-item.exercises');
            },
        }),
        new TextTourStep({
            selector: '.exercise-row-container .control-label',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseRow.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseRow.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            action: () => {
                clickOnElement('.tab-item.exercises');
            },
        }),
        new TextTourStep({
            selector: '.exercise-row-icon',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseType.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseType.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            selector: '.exercise-tags',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseTags.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseTags.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            selector: '.course-information .panel-wrapper',
            headlineTranslateKey: 'tour.courseExerciseOverview.courseInformation.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.courseInformation.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            selector: '.course-information .exercise-panel .panel-wrapper',
            headlineTranslateKey: 'tour.courseExerciseOverview.upcomingDeadlines.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.upcomingDeadlines.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            action: () => {
                clickOnElement('.tab-item.exercises');
            },
        }),
        new TextTourStep({
            selector: '.tab-item.lectures',
            headlineTranslateKey: 'tour.courseExerciseOverview.lectures.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.lectures.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            action: () => {
                clickOnElement('.tab-item.lectures');
            },
            closeAction: () => {
                clickOnElement('.tab-item.lectures');
            },
        }),
        new TextTourStep({
            selector: '.tab-item.statistics',
            headlineTranslateKey: 'tour.courseExerciseOverview.statistics.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.statistics.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            action: () => {
                clickOnElement('.tab-item.statistics');
            },
            closeAction: () => {
                clickOnElement('.tab-item.statistics');
            },
        }),
    ],
};
