import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, ResetParticipation } from 'app/guided-tour/guided-tour.constants';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

export const cancelTour: GuidedTour = {
    settingsKey: 'cancel_tour',
    resetParticipation: ResetParticipation.NONE,
    steps: [
        new TextTourStep({
            highlightSelector: '.guided-tour.dropdown-item',
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

export const completedTour: GuidedTour = {
    settingsKey: 'completed_tour',
    resetParticipation: ResetParticipation.NONE,
    steps: [
        new TextTourStep({
            headlineTranslateKey: 'tour.completed.headline',
            contentTranslateKey: 'tour.completed.content',
            hintTranslateKey: 'tour.completed.hint',
        }),
    ],
};
