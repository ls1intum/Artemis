import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const cancelTour: GuidedTour = {
    courseShortName: '',
    exerciseShortName: '',
    settingsKey: 'cancel_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.dropdown-item.guided-tour',
            headlineTranslateKey: 'tour.cancel.headline',
            contentTranslateKey: 'tour.cancel.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            closeAction: () => {
                clickOnElement('#account-menu[aria-expanded="true"]');
            },
        }),
    ],
};
