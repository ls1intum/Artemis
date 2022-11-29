import { Orientation, ResetParticipation } from 'app/guided-tour/guided-tour.constants';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ImageTourStep, TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Authority } from 'app/shared/constants/authority.constants';

/**
 * This constant contains the guided tour configuration and steps for the course overview page
 */
export const courseOverviewTour: GuidedTour = {
    settingsKey: 'course_overview_tour',
    resetParticipation: ResetParticipation.NONE,
    steps: [
        new ImageTourStep({
            headlineTranslateKey: 'tour.courseOverview.welcome.headline',
            subHeadlineTranslateKey: 'tour.courseOverview.welcome.subHeadline',
            contentTranslateKey: 'tour.courseOverview.welcome.content',
            imageUrl: 'https://ase.in.tum.de/lehrstuhl_1/images/teaching/interactive/InteractiveLearning.png',
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour-overview',
            headlineTranslateKey: 'tour.courseOverview.overviewMenu.headline',
            contentTranslateKey: 'tour.courseOverview.overviewMenu.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOM,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour-course-admin',
            headlineTranslateKey: 'tour.courseOverview.courseAdminMenu.headline',
            contentTranslateKey: 'tour.courseOverview.courseAdminMenu.content',
            orientation: Orientation.BOTTOMRIGHT,
            highlightPadding: 10,
            permission: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour-admin',
            headlineTranslateKey: 'tour.courseOverview.adminMenu.headline',
            contentTranslateKey: 'tour.courseOverview.adminMenu.content',
            orientation: Orientation.BOTTOMRIGHT,
            highlightPadding: 10,
            permission: [Authority.ADMIN],
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour-notification',
            headlineTranslateKey: 'tour.courseOverview.notificationMenu.headline',
            contentTranslateKey: 'tour.courseOverview.notificationMenu.content',
            orientation: Orientation.BOTTOMRIGHT,
            highlightPadding: 10,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour-account',
            headlineTranslateKey: 'tour.courseOverview.accountMenu.headline',
            contentTranslateKey: 'tour.courseOverview.accountMenu.content',
            orientation: Orientation.BOTTOMRIGHT,
            highlightPadding: 10,
        }),
        new TextTourStep({
            highlightSelector: '.card.guided-tour',
            headlineTranslateKey: 'tour.courseOverview.course.headline',
            contentTranslateKey: 'tour.courseOverview.course.content',
            orientation: Orientation.RIGHT,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour .card-footer',
            headlineTranslateKey: 'tour.courseOverview.courseFooter.headline',
            contentTranslateKey: 'tour.courseOverview.courseFooter.content',
            orientation: Orientation.TOPLEFT,
        }),
        new TextTourStep({
            highlightSelector: '.guided-tour-footer-about',
            headlineTranslateKey: 'tour.courseOverview.contact.headline',
            contentTranslateKey: 'tour.courseOverview.contact.content',
            hintTranslateKey: 'tour.courseOverview.contact.hint',
            highlightPadding: 5,
            orientation: Orientation.TOPLEFT,
        }),
    ],
};
