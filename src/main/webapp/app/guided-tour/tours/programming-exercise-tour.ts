import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Orientation } from 'app/guided-tour/guided-tour.constants';
import { TextTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';
import { clickOnElement } from 'app/guided-tour/guided-tour.utils';

/**
 * This constant contains the guided tour configuration and steps for the text exercise info page
 */

export const programmingExerciseTour: GuidedTour = {
    settingsKey: 'programming_exercise_tour',
    steps: [
        new TextTourStep({
            selector: 'jhi-code-editor-file-browser',
            headlineTranslateKey: 'tour.course-overview.overview-menu.headline',
            contentTranslateKey: 'tour.course-overview.overview-menu.content',
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            selector: '.editor-center',
            headlineTranslateKey: 'tour.course-overview.course-admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.course-admin-menu.content',
            orientation: Orientation.RIGHT,
            action: () => {
                clickOnElement('jhi-code-editor-file-browser-file');
            },
        }),
        new TextTourStep({
            selector: '.instructions-wrapper',
            headlineTranslateKey: 'tour.course-overview.admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.admin-menu.content',
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            selector: '.panel-wrapper',
            headlineTranslateKey: 'tour.course-overview.admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.admin-menu.content',
            orientation: Orientation.LEFT,
            highlightPadding: 10,
        }),
    ],
};
