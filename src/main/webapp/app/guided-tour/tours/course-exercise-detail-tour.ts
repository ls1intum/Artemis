import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const programmingExerciseDetailTour: GuidedTour = {
    courseTitle: 'Einführung in die Softwaretechnik',
    exerciseTitle: 'Programming Exercise',
    settingsKey: 'programming_exercise_detail_tour',
    steps: [
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .success',
            headlineTranslateKey: 'tour.programmingExercise.testSuccess.headline',
            contentTranslateKey: 'tour.programmingExercise.testSuccess.content',
            highlightPadding: 20,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .failed',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .test-status--linked',
            eventListenerSelector: 'body',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
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
        }),
    ],
};
