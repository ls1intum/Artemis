import { Component, Input } from '@angular/core';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';

@Component({
    selector: 'jhi-assessment-dashboard-information',
    templateUrl: './assessment-dashboard-information.component.html',
})
export class AssessmentDashboardInformationComponent {
    @Input() isExamMode: boolean;
    @Input() numberOfTutorAssessments: number;
    @Input() courseId: number;
    @Input() complaintsEnabled: boolean;
    @Input() feedbackRequestEnabled: boolean;
    @Input() tutorId: number;
    @Input() numberOfTutorComplaints: number;
    @Input() numberOfTutorMoreFeedbackRequests: number;
    @Input() numberOfAssessmentLocks: number;
    @Input() totalNumberOfAssessmentLocks: number;

    @Input() examId?: number;

    @Input() totalNumberOfAssessments: DueDateStat;
    @Input() numberOfSubmissions: DueDateStat;
    @Input() numberOfCorrectionRounds: number;
    @Input() totalAssessmentPercentage: number;
    @Input() numberOfAssessmentsOfCorrectionRounds: DueDateStat[];
    @Input() isAtLeastInstructor: boolean;
    @Input() numberOfComplaints: number;
    @Input() numberOfMoreFeedbackRequests: number;
}
