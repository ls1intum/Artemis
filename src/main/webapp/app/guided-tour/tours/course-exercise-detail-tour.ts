import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const programmingExerciseDetailTour: GuidedTour = {
    courseTitle: 'Introduction to Software Engineering',
    exerciseTitle: 'G01E01 Programming Git Tutorial',
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
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .text-danger.test-status--linked',
            eventListenerSelector: 'body',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 5,
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
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions [id^=plantUml]',
            headlineTranslateKey: 'tour.programmingExercise.umlFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.umlFailure.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            headlineTranslateKey: 'tour.programmingExercise.resolveTasks.headline',
            contentTranslateKey: 'tour.programmingExercise.resolveTasks.content',
        }),
    ],
};

export const programmingExerciseSuccess: GuidedTour = {
    courseTitle: 'Introduction to Software Engineering',
    exerciseTitle: 'G01E01 Programming Git Tutorial',
    settingsKey: 'programming_exercise_success_tour',
    steps: [
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-step-wizard',
            headlineTranslateKey: 'tour.programmingExercise.reviewResult.headline',
            contentTranslateKey: 'tour.programmingExercise.reviewResult.content',
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions [id^=plantUml]',
            headlineTranslateKey: 'tour.programmingExercise.umlSuccess.headline',
            contentTranslateKey: 'tour.programmingExercise.umlSuccess.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            headlineTranslateKey: 'tour.programmingExercise.exerciseSuccess.headline',
            contentTranslateKey: 'tour.programmingExercise.exerciseSuccess.content',
        }),
    ],
};
