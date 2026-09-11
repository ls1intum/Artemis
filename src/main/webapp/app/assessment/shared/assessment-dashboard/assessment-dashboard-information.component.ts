import { Component, computed, input } from '@angular/core';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiPanelComponent } from '@tumaet/ui-angular';

export class AssessmentDashboardInformationEntry {
    constructor(
        public total: number,
        public tutor: number,
        public done?: number,
    ) {}

    /**
     * Computes the percentage of done/total ratio and returns it as a string
     */
    get doneToTotalPercentage(): string {
        if (this.done == undefined) {
            return '';
        }

        if (this.total === 0) {
            return '100%';
        }

        return `${((100 * this.done) / this.total).toFixed().toString()}%`;
    }
}

@Component({
    selector: 'jhi-assessment-dashboard-information',
    templateUrl: './assessment-dashboard-information.component.html',
    imports: [TranslateDirective, RouterLink, ArtemisTranslatePipe, TumUiPanelComponent],
})
export class AssessmentDashboardInformationComponent {
    readonly isExamMode = input.required<boolean>();
    readonly course = input.required<Course>();
    readonly examId = input<number>();
    readonly tutorId = input.required<number>();

    readonly complaintsEnabled = input.required<boolean>();
    readonly feedbackRequestEnabled = input.required<boolean>();

    readonly numberOfCorrectionRounds = input.required<number>();
    readonly numberOfAssessmentsOfCorrectionRounds = input.required<DueDateStat[]>();

    readonly totalNumberOfAssessments = input.required<number>();
    readonly numberOfSubmissions = input.required<DueDateStat>();
    readonly numberOfTutorAssessments = input.required<number>();
    readonly totalAssessmentPercentage = input.required<number>();

    readonly complaints = input.required<AssessmentDashboardInformationEntry>();
    readonly moreFeedbackRequests = input.required<AssessmentDashboardInformationEntry>();
    readonly assessmentLocks = input.required<AssessmentDashboardInformationEntry>();
    readonly ratings = input.required<AssessmentDashboardInformationEntry>();

    /** Number of completed assessment slots represented by the progress summary. */
    readonly totalProgressItems = computed(() => this.numberOfSubmissions().total * (this.isExamMode() ? this.numberOfCorrectionRounds() : 1));

    readonly assessedSubmissions = computed(() => {
        if (this.isExamMode()) {
            return this.totalNumberOfAssessments();
        }

        const correctionRounds = this.numberOfCorrectionRounds();
        return correctionRounds > 0 ? Math.floor(this.totalNumberOfAssessments() / correctionRounds) : 0;
    });

    /** Locked assessment slots are currently being assessed and are therefore shown separately from open assessment slots. */
    readonly inProgressSubmissions = computed(() => Math.min(Math.max(0, this.totalProgressItems() - this.assessedSubmissions()), this.assessmentLocks().total));

    readonly openSubmissions = computed(() => Math.max(0, this.totalProgressItems() - this.assessedSubmissions() - this.inProgressSubmissions()));

    readonly assessedPercentage = computed(() => this.toSubmissionPercentage(this.assessedSubmissions()));
    readonly inProgressPercentage = computed(() => this.toSubmissionPercentage(this.inProgressSubmissions()));

    readonly openComplaints = computed(() => this.openEntryCount(this.complaints()));
    readonly openMoreFeedbackRequests = computed(() => this.openEntryCount(this.moreFeedbackRequests()));

    readonly complaintsLink = computed(() => {
        const examRouteIfNeeded = this.isExamMode() ? ['exams', this.examId()!] : [];
        return ['/course-management', this.course().id].concat(examRouteIfNeeded).concat(['complaints']);
    });
    readonly moreFeedbackRequestsLink = computed(() => {
        const examRouteIfNeeded = this.isExamMode() ? ['exams', this.examId()!] : [];
        return ['/course-management', this.course().id].concat(examRouteIfNeeded).concat(['more-feedback-requests']);
    });
    readonly assessmentLocksLink = computed(() => {
        const examRouteIfNeeded = this.isExamMode() ? ['exams', this.examId()!] : [];
        return ['/course-management', this.course().id].concat(examRouteIfNeeded).concat(['assessment-locks']);
    });
    readonly ratingsLink = computed(() => ['/course-management', this.course().id, 'ratings']);

    private openEntryCount(entry: AssessmentDashboardInformationEntry): number {
        return Math.max(0, entry.total - (entry.done ?? 0));
    }

    private toSubmissionPercentage(submissions: number): number {
        const totalProgressItems = this.totalProgressItems();
        return totalProgressItems > 0 ? (submissions / totalProgressItems) * 100 : 0;
    }
}
