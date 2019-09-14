import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';

export const codeEditorTour: GuidedTour = {
    settingsKey: 'code_editor_tour',
    steps: [
        new TextTourStep({
            selector: '.editor-sidebar-left .file-browser',
            headlineTranslateKey: 'tour.programmingExercise.fileBrowser.headline',
            contentTranslateKey: 'tour.programmingExercise.fileBrowser.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            selector: '.list-group-item.file-item:not(.node-selected)',
            eventListenerSelector: '.ace_text-layer',
            headlineTranslateKey: 'tour.programmingExercise.exerciseFile.headline',
            contentTranslateKey: 'tour.programmingExercise.exerciseFile.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            selector: '.editor-center',
            eventListenerSelector: '.ace_text-layer',
            headlineTranslateKey: 'tour.programmingExercise.codeEditor.headline',
            contentTranslateKey: 'tour.programmingExercise.codeEditor.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.ACE_EDITOR,
        }),
        new TextTourStep({
            selector: '#save_button',
            headlineTranslateKey: 'tour.programmingExercise.saveChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.saveChanges.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            autoNextStep: false,
        }),
        new TextTourStep({
            selector: '#submit_button',
            eventListenerSelector: 'jhi-updating-result',
            headlineTranslateKey: 'tour.programmingExercise.submitChanges.headline',
            contentTranslateKey: 'tour.programmingExercise.submitChanges.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            selector: 'jhi-updating-result',
            headlineTranslateKey: 'tour.programmingExercise.buildStatus.headline',
            contentTranslateKey: 'tour.programmingExercise.buildStatus.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
        }),
    ],
};
