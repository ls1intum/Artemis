import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Orientation, ResetParticipation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { TextTourStep, UserInterActionTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';

export const courseExerciseOverviewTour: GuidedTour = {
    settingsKey: 'course_exercise_overview_tour',
    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
    steps: [
        new VideoTourStep({
            headlineTranslateKey: 'tour.courseExerciseOverview.installPrerequisites.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.installPrerequisites.content',
            videoUrl: 'tour.courseExerciseOverview.installPrerequisites.videoUrl',
        }),
        new TextTourStep({
            highlightSelector: '.tab-item.exercises',
            headlineTranslateKey: 'tour.courseExerciseOverview.exercises.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exercises.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour.tab-item.statistics',
            headlineTranslateKey: 'tour.courseExerciseOverview.statistics.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.statistics.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour.exercise-row-container .control-label',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseRow.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseRow.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
        }),
        new TextTourStep({
            highlightSelector: '.course-exercise-row.guided-tour',
            headlineTranslateKey: 'tour.courseExerciseOverview.currentExercise.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.currentExercise.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
        }),
        new TextTourStep({
            highlightSelector: '.course-exercise-row.guided-tour .exercise-row-icon',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseType.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseType.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
        }),
        new TextTourStep({
            highlightSelector: '.course-exercise-row.guided-tour .exercise-tags',
            headlineTranslateKey: 'tour.courseExerciseOverview.exerciseTags.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.exerciseTags.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour.course-information .guided-tour.panel-wrapper',
            headlineTranslateKey: 'tour.courseExerciseOverview.courseInformation.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.courseInformation.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour.course-information .guided-tour.exercise-panel .guided-tour.panel-wrapper',
            headlineTranslateKey: 'tour.courseExerciseOverview.upcomingDueDates.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.upcomingDueDates.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.course-exercise-row.guided-tour .start-exercise',
            headlineTranslateKey: 'tour.courseExerciseOverview.startExercise.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.startExercise.content',
            highlightPadding: 5,
            orientation: Orientation.TOPLEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.course-exercise-row.guided-tour .start-exercise',
            headlineTranslateKey: 'tour.courseExerciseOverview.startExerciseWait.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.startExerciseWait.content',
            highlightPadding: 5,
            orientation: Orientation.TOPLEFT,
            userInteractionEvent: UserInteractionEvent.WAIT_FOR_SELECTOR,
            triggerNextStep: true,
        }),
        new TextTourStep({
            highlightSelector: '.course-exercise-row.guided-tour .clone-repository',
            headlineTranslateKey: 'tour.courseExerciseOverview.repositoryCreated.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.repositoryCreated.content',
            highlightPadding: 5,
            orientation: Orientation.TOPLEFT,
        }),
        new VideoTourStep({
            headlineTranslateKey: 'tour.courseExerciseOverview.cloneRepository.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.cloneRepository.content',
            hintTranslateKey: 'tour.courseExerciseOverview.cloneRepository.hint',
            videoUrl: 'tour.courseExerciseOverview.cloneRepository.videoUrl',
        }),
        new VideoTourStep({
            headlineTranslateKey: 'tour.courseExerciseOverview.solveExercise.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.solveExercise.content',
            videoUrl: 'tour.courseExerciseOverview.solveExercise.videoUrl',
        }),
        new TextTourStep({
            highlightSelector: '.course-exercise-row.guided-tour',
            headlineTranslateKey: 'tour.courseExerciseOverview.reviewResult.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.reviewResult.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
        }),
    ],
};
