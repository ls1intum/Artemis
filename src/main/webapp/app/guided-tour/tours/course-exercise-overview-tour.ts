import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { TextTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const courseExerciseOverviewTour: GuidedTour = {
    settingsKey: 'course_exercise_overview_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.tab-item.exercises',
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
            highlightSelector: '.exercise-row-container .control-label',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseRow.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseRow.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            action: () => {
                clickOnElement('.tab-item.exercises');
            },
        }),
        new TextTourStep({
            highlightSelector: '.exercise-row-icon',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseType.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseType.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.exercise-tags',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseTags.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseTags.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.course-information .panel-wrapper',
            headlineTranslateKey: 'tour.courseExerciseOverview.courseInformation.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.courseInformation.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            highlightSelector: '.course-information .exercise-panel .panel-wrapper',
            headlineTranslateKey: 'tour.courseExerciseOverview.upcomingDeadlines.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.upcomingDeadlines.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            action: () => {
                clickOnElement('.tab-item.exercises');
            },
        }),
        new TextTourStep({
            highlightSelector: '.tab-item.lectures',
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
            highlightSelector: '.tab-item.statistics',
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
        new TextTourStep({
            highlightSelector: '.start-exercise',
            headlineTranslateKey: 'tour.courseExerciseOverview.startExercise.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.startExercise.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            action: () => {
                clickOnElement('.tab-item.exercises');
            },
        }),
    ],
};

export const cloneRepositoryTour: GuidedTour = {
    settingsKey: 'clone_repository_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.clone-repository',
            headlineTranslateKey: 'tour.cloneRepository.clone.headline',
            contentTranslateKey: 'tour.cloneRepository.clone.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.popover',
            headlineTranslateKey: 'tour.cloneRepository.useSourceTree.headline',
            contentTranslateKey: 'tour.cloneRepository.useSourceTree.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            action: () => {
                clickOnElement('.clone-repository');
            },
            closeAction: () => {
                clickOnElement('.clone-repository');
            },
        }),
        new VideoTourStep({
            headlineTranslateKey: 'tour.cloneRepository.sourceTreeTutorial.headline',
            contentTranslateKey: 'tour.cloneRepository.sourceTreeTutorial.content',
            videoUrl: 'https://www.youtube.com/embed/cyWZFFS9Q1w?start=61',
        }),
    ],
};
