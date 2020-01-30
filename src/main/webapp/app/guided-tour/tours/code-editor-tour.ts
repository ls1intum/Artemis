import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const codeEditorTour: GuidedTour = {
    settingsKey: 'code_editor_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.editor-sidebar-left .file-browser',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.fileBrowser.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.fileBrowser.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.list-group-item.file-item:not(.node-selected)',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.exerciseFile.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.exerciseFile.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.editor-center',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.aceEditor.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.aceEditor.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.ACE_EDITOR,
        }),
        new UserInterActionTourStep({
            highlightSelector: '#save_button',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.saveChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.saveChanges.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: '#submit_button',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.submitChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.submitChanges.content',
            hintTranslateKey: 'tour.programmingExercise.codeEditor.submitChanges.hint',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-updating-result',
            headlineTranslateKey: 'tour.programmingExercise.buildLoading.headline',
            contentTranslateKey: 'tour.programmingExercise.buildLoading.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.WAIT_FOR_SELECTOR,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-updating-result .text-success',
            headlineTranslateKey: 'tour.programmingExercise.buildStatus.headline',
            contentTranslateKey: 'tour.programmingExercise.buildStatus.content',
            hintTranslateKey: 'tour.programmingExercise.buildStatus.hint',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            skipStepIfNoSelector: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-updating-result .text-danger .result',
            headlineTranslateKey: 'tour.programmingExercise.buildStatus.headline',
            contentTranslateKey: 'tour.programmingExercise.buildStatus.content',
            hintTranslateKey: 'tour.programmingExercise.buildStatus.hint',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            skipStepIfNoSelector: true,
        }),
        new TextTourStep({
            highlightSelector: '.modal-body pre',
            headlineTranslateKey: 'tour.programmingExercise.testCases.headline',
            contentTranslateKey: 'tour.programmingExercise.testCases.content',
            highlightPadding: 20,
            orientation: Orientation.BOTTOM,
            skipStepIfNoSelector: true,
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
            skipStepIfNoSelector: true,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .failed',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.LEFT,
            skipStepIfNoSelector: true,
        }),
    ],
};
