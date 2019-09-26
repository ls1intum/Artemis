import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { TextTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';

export const courseExerciseOverviewTour: GuidedTour = {
    courseShortName: 'artemistutorial',
    exerciseShortName: 'tutorial',
    settingsKey: 'course_exercise_overview_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.tab-item.exercises',
            headlineTranslateKey: 'tour.courseExerciseOverview.exercises.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exercises.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.tab-item.lectures',
            headlineTranslateKey: 'tour.courseExerciseOverview.lectures.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.lectures.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.tab-item.statistics',
            headlineTranslateKey: 'tour.courseExerciseOverview.statistics.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.statistics.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.exercise-row-container .control-label',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseRow.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseRow.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOM,
        }),
        new TextTourStep({
            highlightSelector: '.row.guided-tour',
            headlineTranslateKey: 'tour.courseExerciseOverview.currentExercise.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.currentExercise.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOM,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour .exercise-row-icon',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseType.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseType.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMLEFT,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour .exercise-tags',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseTags.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseTags.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
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
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour .start-exercise',
            headlineTranslateKey: 'tour.courseExerciseOverview.startExercise.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.startExercise.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new VideoTourStep({
            headlineTranslateKey: 'tour.courseExerciseOverview.cloneRepository.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.cloneRepository.content',
            videoUrl: 'https://www.youtube.com/embed/cyWZFFS9Q1w?start=61',
        }),
    ],
};
