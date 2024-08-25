import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, ResetParticipation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const codeEditorTour: GuidedTour = {
    settingsKey: 'code_editor_tour',
    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
    steps: [
        new TextTourStep({
            highlightSelector: '.editor-sidebar-left .guided-tour-file-browser',
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
            highlightSelector: '.guided-tour-editor',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.monacoEditor.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.monacoEditor.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.MONACO_EDITOR,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.guided-tour-save-button',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.saveChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.saveChanges.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.guided-tour-submit-button',
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
            highlightSelector: 'jhi-updating-result .guided-tour.text-success',
            headlineTranslateKey: 'tour.programmingExercise.buildStatus.headline',
            contentTranslateKey: 'tour.programmingExercise.buildStatus.content',
            hintTranslateKey: 'tour.programmingExercise.buildStatus.hint',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            skipStepIfNoSelector: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-updating-result .guided-tour.text-danger .guided-tour.result',
            headlineTranslateKey: 'tour.programmingExercise.buildStatus.headline',
            contentTranslateKey: 'tour.programmingExercise.buildStatus.content',
            hintTranslateKey: 'tour.programmingExercise.buildStatus.hint',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            skipStepIfNoSelector: true,
            triggerNextStep: true,
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
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .guided-tour.success',
            headlineTranslateKey: 'tour.programmingExercise.testSuccess.headline',
            contentTranslateKey: 'tour.programmingExercise.testSuccess.content',
            highlightPadding: 20,
            orientation: Orientation.LEFT,
            skipStepIfNoSelector: true,
        }),
        new TextTourStep({
            highlightSelector: 'jhi-programming-exercise-instructions-task-status .guided-tour.failed',
            headlineTranslateKey: 'tour.programmingExercise.testFailure.headline',
            contentTranslateKey: 'tour.programmingExercise.testFailure.content',
            highlightPadding: 20,
            orientation: Orientation.LEFT,
            skipStepIfNoSelector: true,
        }),
    ],
};
