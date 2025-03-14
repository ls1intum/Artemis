import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, ResetParticipation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const programmingExerciseFail: GuidedTour = {
    settingsKey: 'programming_exercise_fail_tour',
    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
    steps: [
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .guided-tour.failed',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.RIGHT,
            skipStepIfNoSelector: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .text-danger.guided-tour.test-status--linked',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            skipStepIfNoSelector: true,
        }),
        new TextTourStep({
            highlightSelector: '.modal-content',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.BOTTOM,
            closeAction: () => {
                clickOnElement('.modal-header .close');
            },
            skipStepIfNoSelector: true,
        }),
        new TextTourStep({
            headlineTranslateKey: 'tour.programmingExercise.resolveTasks.headline',
            contentTranslateKey: 'tour.programmingExercise.resolveTasks.content',
        }),
    ],
};

export const programmingExerciseSuccess: GuidedTour = {
    settingsKey: 'programming_exercise_success_tour',
    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
    steps: [
        new TextTourStep({
            highlightSelector: '.guided-tour.instructions__content__markdown',
            headlineTranslateKey: 'tour.programmingExercise.reviewResult.headline',
            contentTranslateKey: 'tour.programmingExercise.reviewResult.content',
            orientation: Orientation.TOP,
        }),
    ],
};
