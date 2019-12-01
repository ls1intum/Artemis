import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { AssessmentTaskTourStep, TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { AssessmentObject, GuidedTourAssessmentTask } from 'app/guided-tour/guided-tour-task.model';

export const tutorCourseDashboardTour: GuidedTour = {
    settingsKey: 'tutor_course_dashboard_tour',
    showCompletedTourStep: false,
    steps: [
        new TextTourStep({
            highlightSelector: '#assessment-statistics',
            headlineTranslateKey: 'tour.tutorCourseDashboard.overview.headline',
            contentTranslateKey: 'tour.tutorCourseDashboard.overview.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
        new UserInterActionTourStep({
            highlightSelector: '.table-responsive .form-check',
            clickEventListenerSelector: '.exercise-table',
            headlineTranslateKey: 'tour.tutorCourseDashboard.showFinished.headline',
            contentTranslateKey: 'tour.tutorCourseDashboard.showFinished.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            permission: ['ROLE_TA'],
            userInteractionEvent: UserInteractionEvent.CLICK,
            checkForNextSelector: true,
        }),
        new TextTourStep({
            highlightSelector: '.exercise-table:not([hidden])',
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
        new UserInterActionTourStep({
            highlightSelector: '.btn.guided-tour',
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
        new UserInterActionTourStep({
            highlightSelector: '.instructions-button',
            clickEventListenerSelector: '.exercise-dashboard',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.instructionsButton.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.instructionsButton.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
        new UserInterActionTourStep({
            highlightSelector: '.review-example-submission.guided-tour',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.reviewExampleSubmission.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.reviewExampleSubmission.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: ['ROLE_TA'],
        }),
        new UserInterActionTourStep({
            highlightSelector: '.assess-example-submission.guided-tour',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.assessExampleSubmission.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.assessExampleSubmission.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.d-flex .flex-grow-1',
            headlineTranslateKey: 'tour.tutorExerciseDashboard.submissionsAndComplaints.headline',
            contentTranslateKey: 'tour.tutorExerciseDashboard.submissionsAndComplaints.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
    ],
};

export const tutorReviewExampleSubmissionTour: GuidedTour = {
    settingsKey: 'tutor_review_submission_tour',
    showCompletedTourStep: false,
    steps: [
        new TextTourStep({
            highlightSelector: '.col-12 > .row.flex-nowrap',
            headlineTranslateKey: 'tour.exampleReview.reviewSubmission.headline',
            contentTranslateKey: 'tour.exampleReview.reviewSubmission.content',
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
        new TextTourStep({
            highlightSelector: '.text-assessments',
            headlineTranslateKey: 'tour.exampleReview.reviewAssessment.headline',
            contentTranslateKey: 'tour.exampleReview.reviewAssessment.content',
            orientation: Orientation.TOP,
            permission: ['ROLE_TA'],
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-example-text-submission .btn-success',
            headlineTranslateKey: 'tour.exampleReview.confirm.headline',
            contentTranslateKey: 'tour.exampleReview.confirm.content',
            userInteractionEvent: UserInteractionEvent.CLICK,
            orientation: Orientation.TOPRIGHT,
            highlightPadding: 10,
            permission: ['ROLE_TA'],
        }),
    ],
};

export const tutorAssessExampleSubmissionTour: GuidedTour = {
    settingsKey: 'tutor_assess_submission_tour',
    showCompletedTourStep: false,
    steps: [
        new AssessmentTaskTourStep({
            highlightSelector: '.col-12 > .row.flex-nowrap',
            headlineTranslateKey: 'tour.exampleAssessment.addAssessment.headline',
            contentTranslateKey: 'tour.exampleAssessment.addAssessment.content',
            orientation: Orientation.TOP,
            highlightPadding: 10,
            permission: ['ROLE_TA'],
            userInteractionEvent: UserInteractionEvent.ASSESS_SUBMISSION,
            checkForNextSelector: true,
            assessmentTask: new GuidedTourAssessmentTask('tour.exampleAssessment.addAssessment.task', new AssessmentObject(3, 0)),
        }),
        new AssessmentTaskTourStep({
            highlightSelector: '.text-assessments .row',
            headlineTranslateKey: 'tour.exampleAssessment.addScore.headline',
            contentTranslateKey: 'tour.exampleAssessment.addScore.content',
            userInteractionEvent: UserInteractionEvent.ASSESS_SUBMISSION,
            orientation: Orientation.TOP,
            highlightPadding: 10,
            permission: ['ROLE_TA'],
            assessmentTask: new GuidedTourAssessmentTask('tour.exampleAssessment.addScore.task', new AssessmentObject(3, 3)),
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-example-text-submission .btn-primary',
            clickEventListenerSelector: '.alerts',
            headlineTranslateKey: 'tour.exampleAssessment.submit.headline',
            contentTranslateKey: 'tour.exampleAssessment.submit.content',
            orientation: Orientation.TOPRIGHT,
            highlightPadding: 10,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: ['ROLE_TA'],
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-example-text-submission .back-button',
            headlineTranslateKey: 'tour.exampleAssessment.back.headline',
            contentTranslateKey: 'tour.exampleAssessment.back.content',
            orientation: Orientation.RIGHT,
            highlightPadding: 10,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: ['ROLE_TA'],
        }),
    ],
};
