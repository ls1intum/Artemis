import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ContentType, Orientation } from 'app/guided-tour/guided-tour.constants';

/**
 * This constant contains the guided tour configuration and steps for the text exercise info page
 */

export const textExerciseTour: GuidedTour = {
    settingsId: 'showTextExerciseTour',
    steps: [
        {
            contentType: ContentType.TEXT,
            selector: '.general-info',
            headlineTranslateKey: 'tour.course-overview.overview-menu.headline',
            contentTranslateKey: 'tour.course-overview.overview-menu.content',
            orientation: Orientation.BOTTOM,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.course-info-bar .row h5',
            headlineTranslateKey: 'tour.course-overview.course-admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.course-admin-menu.content',
            orientation: Orientation.BOTTOM,
        },
        {
            contentType: ContentType.TEXT,
            selector: 'jhi-exercise-details-student-actions button',
            headlineTranslateKey: 'tour.course-overview.admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.admin-menu.content',
            orientation: Orientation.RIGHT,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.panel-wrapper',
            headlineTranslateKey: 'tour.course-overview.admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.admin-menu.content',
            orientation: Orientation.LEFT,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
    ],
};
