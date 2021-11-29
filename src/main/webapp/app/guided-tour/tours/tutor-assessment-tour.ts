import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { AssessmentTaskTourStep, TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, ResetParticipation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { AssessmentObject, GuidedTourAssessmentTask } from 'app/guided-tour/guided-tour-task.model';
import { Authority } from 'app/shared/constants/authority.constants';

export const tutorAssessmentTour: GuidedTour = {
    settingsKey: 'tutor_assessment_tour',
    resetParticipation: ResetParticipation.TUTOR_ASSESSMENT,
    steps: [
        // step 1
        new UserInterActionTourStep({
            highlightSelector: '.guided-tour-assessment-dashboard-btn',
            headlineTranslateKey: 'tour.courseAdministration.assessmentDashboardButton.headline',
            contentTranslateKey: 'tour.courseAdministration.assessmentDashboardButton.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            permission: [Authority.TA],
            pageUrl: '/course-management',
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        // step 2
        // new page
        new TextTourStep({
            highlightSelector: '.guided-tour-assessment-stats',
            headlineTranslateKey: 'tour.assessmentDashboard.overview.headline',
            contentTranslateKey: 'tour.assessmentDashboard.overview.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
            pageUrl: '/course-management/(\\d+)+/assessment-dashboard',
        }),
        // step 3
        new TextTourStep({
            highlightSelector: '.guided-tour-form-check',
            headlineTranslateKey: 'tour.assessmentDashboard.showFinished.headline',
            contentTranslateKey: 'tour.assessmentDashboard.showFinished.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            permission: [Authority.TA],
        }),
        // step 4
        new TextTourStep({
            highlightSelector: '.guided-tour-exercise-table',
            headlineTranslateKey: 'tour.assessmentDashboard.exerciseTable.headline',
            contentTranslateKey: 'tour.assessmentDashboard.exerciseTable.content',
            highlightPadding: 25,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
        }),
        // step 5
        new TextTourStep({
            highlightSelector: 'jhi-tutor-participation-graph.guided-tour',
            headlineTranslateKey: 'tour.assessmentDashboard.gradingProcess.headline',
            contentTranslateKey: 'tour.assessmentDashboard.gradingProcess.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
        }),
        // step 6
        new UserInterActionTourStep({
            highlightSelector: '.btn.guided-tour',
            headlineTranslateKey: 'tour.assessmentDashboard.exerciseDashboardButton.headline',
            contentTranslateKey: 'tour.assessmentDashboard.exerciseDashboardButton.content',
            highlightPadding: 10,
            orientation: Orientation.TOPRIGHT,
            permission: [Authority.TA],
            userInteractionEvent: UserInteractionEvent.CLICK,
        }),
        // step 7
        // new page
        new TextTourStep({
            highlightSelector: '.guided-tour-markdown-preview',
            headlineTranslateKey: 'tour.exerciseAssessmentDashboard.instructions.headline',
            contentTranslateKey: 'tour.exerciseAssessmentDashboard.instructions.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
            pageUrl: '/course-management/(\\d+)+/assessment-dashboard/(\\d+)+',
        }),
        // step 8
        new UserInterActionTourStep({
            highlightSelector: '.guided-tour-instructions-button',
            headlineTranslateKey: 'tour.exerciseAssessmentDashboard.instructionsButton.headline',
            contentTranslateKey: 'tour.exerciseAssessmentDashboard.instructionsButton.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
        // step 9
        new UserInterActionTourStep({
            highlightSelector: '.review-example-submission.guided-tour',
            headlineTranslateKey: 'tour.exerciseAssessmentDashboard.readExampleSubmission.headline',
            contentTranslateKey: 'tour.exerciseAssessmentDashboard.readExampleSubmission.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: [Authority.TA],
        }),
        // step 10
        // new page
        new TextTourStep({
            highlightSelector: '.guided-tour-assessment-editor',
            headlineTranslateKey: 'tour.exampleRead.readSubmission.headline',
            contentTranslateKey: 'tour.exampleRead.readSubmission.content',
            orientation: Orientation.TOP,
            permission: [Authority.TA],
            pageUrl: 'course-management/(\\d+)+/text-exercises/(\\d+)+/example-submissions/(\\d+)+?readOnly=true',
        }),
        // step 11
        new TextTourStep({
            highlightSelector: '.guided-tour-text-assessment',
            headlineTranslateKey: 'tour.exampleRead.readAssessment.headline',
            contentTranslateKey: 'tour.exampleRead.readAssessment.content',
            orientation: Orientation.TOP,
            permission: [Authority.TA],
        }),
        // step 12
        new UserInterActionTourStep({
            highlightSelector: 'jhi-example-text-submission .guided-tour-read',
            headlineTranslateKey: 'tour.exampleRead.confirm.headline',
            contentTranslateKey: 'tour.exampleRead.confirm.content',
            userInteractionEvent: UserInteractionEvent.CLICK,
            orientation: Orientation.TOPRIGHT,
            highlightPadding: 10,
            permission: [Authority.TA],
        }),
        // step 13
        // new page
        new UserInterActionTourStep({
            highlightSelector: '.assess-example-submission.guided-tour',
            headlineTranslateKey: 'tour.exerciseAssessmentDashboard.assessExampleSubmission.headline',
            contentTranslateKey: 'tour.exerciseAssessmentDashboard.assessExampleSubmission.content',
            highlightPadding: 10,
            orientation: Orientation.TOPLEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: [Authority.TA],
            pageUrl: '/course-management/(\\d+)+/assessment-dashboard/(\\d+)+',
        }),
        // step 14
        // new page
        new AssessmentTaskTourStep({
            highlightSelector: '.guided-tour-complete-assessment-editor',
            headlineTranslateKey: 'tour.exampleAssessment.addAssessment.headline',
            contentTranslateKey: 'tour.exampleAssessment.addAssessment.content',
            orientation: Orientation.TOP,
            highlightPadding: 10,
            permission: [Authority.TA],
            userInteractionEvent: UserInteractionEvent.ASSESS_SUBMISSION,
            assessmentTask: new GuidedTourAssessmentTask('tour.exampleAssessment.addAssessment.task', new AssessmentObject(3, 0)),
            pageUrl: 'course-management/(\\d+)+/text-exercises/(\\d+)+/example-submissions/(\\d+)+?toComplete=true',
        }),
        // step 15
        new AssessmentTaskTourStep({
            highlightSelector: '.guided-tour-assessment-editor',
            headlineTranslateKey: 'tour.exampleAssessment.addScore.headline',
            contentTranslateKey: 'tour.exampleAssessment.addScore.content',
            userInteractionEvent: UserInteractionEvent.ASSESS_SUBMISSION,
            orientation: Orientation.TOP,
            highlightPadding: 10,
            permission: [Authority.TA],
            assessmentTask: new GuidedTourAssessmentTask('tour.exampleAssessment.addScore.task', new AssessmentObject(3, 3)),
        }),
        // step 16
        new UserInterActionTourStep({
            highlightSelector: 'jhi-example-text-submission .guided-tour-check-assessment',
            headlineTranslateKey: 'tour.exampleAssessment.submit.headline',
            contentTranslateKey: 'tour.exampleAssessment.submit.content',
            orientation: Orientation.TOPRIGHT,
            highlightPadding: 10,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: [Authority.TA],
        }),
        // step 17
        new UserInterActionTourStep({
            highlightSelector: 'jhi-example-text-submission .guided-tour-back',
            headlineTranslateKey: 'tour.exampleAssessment.back.headline',
            contentTranslateKey: 'tour.exampleAssessment.back.content',
            orientation: Orientation.RIGHT,
            highlightPadding: 10,
            userInteractionEvent: UserInteractionEvent.CLICK,
            permission: [Authority.TA],
        }),
        // step 18
        // new page
        new TextTourStep({
            highlightSelector: '.guided-tour-exercise-dashboard-table',
            headlineTranslateKey: 'tour.exerciseAssessmentDashboard.submissionsAndComplaints.headline',
            contentTranslateKey: 'tour.exerciseAssessmentDashboard.submissionsAndComplaints.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
            pageUrl: '/course-management/(\\d+)+/assessment-dashboard/(\\d+)+',
        }),
        // step 19
        new TextTourStep({
            highlightSelector: '.guided-tour-new-assessment-btn',
            headlineTranslateKey: 'tour.exerciseAssessmentDashboard.assessSubmissions.headline',
            contentTranslateKey: 'tour.exerciseAssessmentDashboard.assessSubmissions.content',
            highlightPadding: 10,
            orientation: Orientation.TOP,
            permission: [Authority.TA],
        }),
    ],
};
