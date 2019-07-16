import { ContentType, GuidedTour, LinkType, Orientation } from 'app/guided-tour/guided-tour.constants';

/**
 * This constant contains the guided tour configuration and steps for the course overview page
 */
export const courseOverviewTour: GuidedTour = {
    settingsId: 'showCourseOverviewTour',
    useOrb: false,
    steps: [
        {
            contentType: ContentType.IMAGE,
            headlineTranslateKey: 'tour.course-overview.welcome.headline',
            subHeadlineTranslateKey: 'tour.course-overview.welcome.subHeadline',
            contentTranslateKey: 'tour.course-overview.welcome.content',
            imageUrl: 'https://ase.in.tum.de/lehrstuhl_1/images/teaching/interactive/InteractiveLearning.png',
        },
        {
            contentType: ContentType.TEXT,
            selector: '#overview-menu',
            headlineTranslateKey: 'tour.course-overview.overview-menu.headline',
            contentTranslateKey: 'tour.course-overview.overview-menu.content',
            orientation: Orientation.BottomLeft,
        },
        {
            contentType: ContentType.TEXT,
            selector: '#course-admin-menu',
            headlineTranslateKey: 'tour.course-overview.course-admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.course-admin-menu.content',
            orientation: Orientation.BottomLeft,
            permission: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
        },
        {
            contentType: ContentType.TEXT,
            selector: '#admin-menu',
            headlineTranslateKey: 'tour.course-overview.admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.admin-menu.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
            permission: ['ROLE_ADMIN'],
            action: () => {
                const currentElement = document.getElementById('admin-menu');
                if (currentElement) {
                    currentElement.click();
                }
            },
        },
        {
            contentType: ContentType.TEXT,
            selector: '.navbar-expand-md .navbar-nav .dropdown-menu',
            headlineTranslateKey: 'tour.course-overview.admin-menu.headline',
            contentTranslateKey: 'tour.course-overview.admin-menu.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
            permission: ['ROLE_ADMIN'],
            action: () => {
                const currentElement = document.getElementById('admin-menu');
                if (currentElement) {
                    currentElement.click();
                }
            },
        },
        {
            contentType: ContentType.TEXT,
            selector: '#notificationsNavBarDropdown',
            headlineTranslateKey: 'tour.course-overview.notification-menu.headline',
            contentTranslateKey: 'tour.course-overview.notification-menu.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '#account-menu',
            headlineTranslateKey: 'tour.course-overview.account-menu.headline',
            contentTranslateKey: 'tour.course-overview.account-menu.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.card-header',
            headlineTranslateKey: 'tour.course-overview.course.headline',
            contentTranslateKey: 'tour.course-overview.course.content',
            orientation: Orientation.Right,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.card-footer',
            headlineTranslateKey: 'tour.course-overview.course-footer.headline',
            contentTranslateKey: 'tour.course-overview.course-footer.content',
            orientation: Orientation.Right,
        },
        {
            contentType: ContentType.TEXT,
            selector: 'jhi-course-registration-selector button',
            headlineTranslateKey: 'tour.course-overview.register.headline',
            contentTranslateKey: 'tour.course-overview.register.content',
            orientation: Orientation.Left,
            useHighlightPadding: true,
            highlightPadding: 10,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.footer .col-sm-6',
            headlineTranslateKey: 'tour.course-overview.contact.headline',
            contentTranslateKey: 'tour.course-overview.contact.content',
            orientation: Orientation.TopLeft,
        },
        {
            contentType: ContentType.VIDEO,
            headlineTranslateKey: 'tour.course-overview.team.headline',
            contentTranslateKey: 'tour.course-overview.team.content',
            externalUrl: 'https://ase.in.tum.de/lehrstuhl_1/people',
            externalUrlTranslateKey: 'tour.course-overview.team.link-text',
            linkType: LinkType.LINK,
            videoUrl: 'https://www.youtube.com/embed/EOyxE9L-4X4',
        },
    ],
};
