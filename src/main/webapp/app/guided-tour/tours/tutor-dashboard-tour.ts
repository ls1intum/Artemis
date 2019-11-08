import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';

export const tutorCourseDashboardTour: GuidedTour = {
    courseShortName: 'artemistutorial',
    exerciseShortName: 'Patterns in Software Engineering',
    settingsKey: 'tutor_course_dashboard_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.tutor-stats',
            headlineTranslateKey: 'tour.tutorCourseDashboard.overview.headline',
            contentTranslateKey: 'tour.tutorCourseDashboard.overview.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.exercise-table',
            headlineTranslateKey: 'tour.tutorCourseDashboard.exerciseTable.headline',
            contentTranslateKey: 'tour.tutorCourseDashboard.exerciseTable.content',
            highlightPadding: 25,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: 'jhi-tutor-participation-graph.guided-tour',
            headlineTranslateKey: 'tour.tutorCourseDashboard.gradingProcess.headline',
            contentTranslateKey: 'tour.tutorCourseDashboard.gradingProcess.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.exercise-dashboard.guided-tour',
            headlineTranslateKey: 'tour.tutorCourseDashboard.exerciseDashboardButton.headline',
            contentTranslateKey: 'tour.tutorCourseDashboard.exerciseDashboardButton.content',
            highlightPadding: 10,
            orientation: Orientation.TOPRIGHT,
            permission: ['ROLE_TA'],
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
    ],
};

export const tutorExerciseDashboardTour: GuidedTour = {
    courseShortName: 'artemistutorial',
    exerciseShortName: 'Patterns in Software Engineering',
    settingsKey: 'tutor_exercise_dashboard_tour',
    steps: [
        new TextTourStep({
            highlightSelector: '.markdown-preview',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.instructions.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.instructions.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.instructions-button',
            eventListenerSelector: '.exercise-dashboard',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.instructionsButton.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.instructionsButton.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
            userInteractionEvent: UserInteractionEvent.CLICK,
            skipStepIfNoSelector: true,
            triggerNextStep: true,
        }),
        new TextTourStep({
            highlightSelector: '.exercise-table',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.studentSubmissions.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.studentSubmissions.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.assessment-button.guided-tour',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.startAssessment.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.startAssessment.content',
            hintTranslateKey: 'tour.tutorExerciseDashboard.startAssessment.hint',
            highlightPadding: 10,
            orientation: Orientation.TOPRIGHT,
            permission: ['ROLE_TA'],
        }),
    ],
};
