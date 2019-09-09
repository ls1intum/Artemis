import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';

export const codeEditorTour: GuidedTour = {
    settingsKey: 'code_editor_tour',
    steps: [
        new TextTourStep({
            selector: '.editor-sidebar-left .file-browser',
            headlineTranslateKey: 'tour.cloneRepository.clone.headline',
            contentTranslateKey: 'tour.cloneRepository.clone.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            selector: '.list-group-item.file-item',
            headlineTranslateKey: 'tour.cloneRepository.clone.headline',
            contentTranslateKey: 'tour.cloneRepository.clone.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        new TextTourStep({
            selector: '.editor-center',
            headlineTranslateKey: 'tour.cloneRepository.clone.headline',
            contentTranslateKey: 'tour.cloneRepository.clone.content',
            highlightPadding: 5,
            orientation: Orientation.RIGHT,
            userInteractionEvent: UserInteractionEvent.ACE_EDITOR,
        }),
        new TextTourStep({
            selector: '#save_button',
            headlineTranslateKey: 'tour.cloneRepository.clone.headline',
            contentTranslateKey: 'tour.cloneRepository.clone.content',
            highlightPadding: 5,
            orientation: Orientation.LEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            autoNextStep: false,
        }),
        new TextTourStep({
            selector: '#submit_button',
            headlineTranslateKey: 'tour.cloneRepository.clone.headline',
            contentTranslateKey: 'tour.cloneRepository.clone.content',
            highlightPadding: 5,
            orientation: Orientation.LEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
    ],
};
