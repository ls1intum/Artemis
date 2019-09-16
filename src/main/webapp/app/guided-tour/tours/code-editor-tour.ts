import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const codeEditorTour: GuidedTour = {
    settingsKey: 'code_editor_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.editor-sidebar-left .file-browser',
            headlineTranslateKey: 'tour.programmingExercise.fileBrowser.headline',
            contentTranslateKey: 'tour.programmingExercise.fileBrowser.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.list-group-item.file-item:not(.node-selected)',
            eventListenerSelector: '.ace_text-layer',
            headlineTranslateKey: 'tour.programmingExercise.exerciseFile.headline',
            contentTranslateKey: 'tour.programmingExercise.exerciseFile.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            highlightSelector: '.editor-center',
            eventListenerSelector: '.ace_text-layer',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.ACE_EDITOR,
        }),
        new TextTourStep({
            highlightSelector: '#save_button',
            headlineTranslateKey: 'tour.programmingExercise.saveChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.saveChanges.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            autoNextStep: false,
        }),
        new TextTourStep({
            highlightSelector: '#submit_button',
            eventListenerSelector: 'jhi-result',
            headlineTranslateKey: 'tour.programmingExercise.submitChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.submitChanges.content',
            hintTranslateKey: 'tour.programmingExercise.submitChanges.hint',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-updating-result',
            headlineTranslateKey: 'tour.programmingExercise.buildLoading.headline',
            contentTranslateKey: 'tour.programmingExercise.buildLoading.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.WAIT_FOR_SELECTOR,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-updating-result .text-danger .result',
            eventListenerSelector: 'body',
            headlineTranslateKey: 'tour.programmingExercise.buildStatus.headline',
            contentTranslateKey: 'tour.programmingExercise.buildStatus.content',
            highlightPadding: 20,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            highlightSelector: '.modal-body pre',
            headlineTranslateKey: 'tour.programmingExercise.testCases.headline',
            contentTranslateKey: 'tour.programmingExercise.testCases.content',
            highlightPadding: 20,
            orientation: Orientation.BOTTOM,
            closeAction: () => {
                clickOnElement('.modal-header .close');
            },
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .success',
            headlineTranslateKey: 'tour.programmingExercise.testSuccess.headline',
            contentTranslateKey: 'tour.programmingExercise.testSuccess.content',
            highlightPadding: 20,
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .failed',
            eventListenerSelector: 'body',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.LEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            highlightSelector: '.modal-body pre',
            headlineTranslateKey: 'tour.programmingExercise.testCases.headline',
            contentTranslateKey: 'tour.programmingExercise.testCases.content',
            highlightPadding: 20,
            orientation: Orientation.BOTTOM,
            closeAction: () => {
                clickOnElement('.modal-header .close');
            },
        }),
    ],
};
