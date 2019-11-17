import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';

export const courseAdministrationTour: GuidedTour = {
    settingsKey: 'course_administration_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.exercise-button.guided-tour',
            headlineTranslateKey: 'tour.courseAdministration.exerciseButton.headline',
            contentTranslateKey: 'tour.courseAdministration.exerciseButton.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.tutor-dashboard.guided-tour',
            headlineTranslateKey: 'tour.courseAdministration.tutorCourseDashboardButton.headline',
            contentTranslateKey: 'tour.courseAdministration.tutorCourseDashboardButton.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
            permission: ['ROLE_TA'],
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
    ],
};
