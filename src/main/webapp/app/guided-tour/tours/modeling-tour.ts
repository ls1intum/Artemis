import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ImageTourStep, TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';

export const modelingTour: GuidedTour = {
    courseShortName: 'artemistutorial',
    exerciseShortName: 'modeling',
    settingsKey: 'modeling_tour',
    steps: [
        new TextTourStep({
            headlineTranslateKey: '',
            contentTranslateKey: '',
        }),
        new ImageTourStep({
            headlineTranslateKey: '',
            contentTranslateKey: '',
            imageUrl: '/../../../content/images/guided-tour-images/uml-add-class.gif',
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: '',
            contentTranslateKey: '',
            highlightPadding: 5,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
        }),
        new ImageTourStep({
            headlineTranslateKey: '',
            contentTranslateKey: '',
            imageUrl: '/../../../content/images/guided-tour-images/uml-edit-class.gif',
        }),
        new ImageTourStep({
            headlineTranslateKey: '',
            contentTranslateKey: '',
            imageUrl: '/../../../content/images/guided-tour-images/uml-create-association.gif',
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: '',
            contentTranslateKey: '',
            highlightPadding: 5,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
        }),
        new TextTourStep({
            highlightSelector: '.save-button',
            headlineTranslateKey: '',
            contentTranslateKey: '',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.submit-button',
            clickEventListenerSelector: 'jhi-result',
            headlineTranslateKey: '',
            contentTranslateKey: '',
            highlightPadding: 10,
            orientation: Orientation.BOTTOMRIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.modeling-editor .btn-warning',
            headlineTranslateKey: '',
            contentTranslateKey: '',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
    ],
};
