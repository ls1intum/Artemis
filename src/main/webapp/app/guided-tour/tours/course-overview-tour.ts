import { ContentType, GuidedTour, Orientation } from 'app/guided-tour/guided-tour.constants';

/**
 * This constant contains the guided tour configuration and steps for the course overview page
 */
export const courseOverviewTour: GuidedTour = {
    settingsId: 'showCourseOverviewTour',
    useOrb: false,
    steps: [
        {
            contentType: ContentType.IMAGE,
            headlineTranslateKey: 'tour.course-overview.1.headline',
            subHeadlineTranslateKey: 'tour.course-overview.1.subHeadline',
            contentTranslateKey: 'tour.course-overview.1.content',
            imageUrl: './../../../../content/images/logo_tum_256.png',
        },
        {
            contentType: ContentType.TEXT,
            selector: '#overview-menu',
            headlineTranslateKey: 'tour.course-overview.2.headline',
            contentTranslateKey: 'tour.course-overview.2.content',
            orientation: Orientation.BottomLeft,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.card-header',
            headlineTranslateKey: 'tour.course-overview.3.headline',
            contentTranslateKey: 'tour.course-overview.3.content',
            orientation: Orientation.Right,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.card-footer',
            headlineTranslateKey: 'tour.course-overview.4.headline',
            contentTranslateKey: 'tour.course-overview.4.content',
            orientation: Orientation.Right,
        },
        {
            contentType: ContentType.TEXT,
            selector: 'jhi-course-registration-selector button',
            headlineTranslateKey: 'tour.course-overview.5.headline',
            contentTranslateKey: 'tour.course-overview.5.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '#notificationsNavBarDropdown',
            headlineTranslateKey: 'tour.course-overview.6.headline',
            contentTranslateKey: 'tour.course-overview.6.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '#account-menu',
            headlineTranslateKey: 'tour.course-overview.7.headline',
            contentTranslateKey: 'tour.course-overview.7.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.footer .col-sm-6',
            headlineTranslateKey: 'tour.course-overview.8.headline',
            contentTranslateKey: 'tour.course-overview.8.content',
            orientation: Orientation.TopLeft,
        },
        {
            contentType: ContentType.VIDEO,
            headlineTranslateKey: 'tour.course-overview.8.headline',
            contentTranslateKey: 'tour.course-overview.8.content',
            videoUrl: 'https://www.youtube.com/embed/EOyxE9L-4X4',
        },
    ],
};
