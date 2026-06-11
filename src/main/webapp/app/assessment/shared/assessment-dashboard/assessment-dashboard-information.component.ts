import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { LegendPosition, PieChartModule } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { SidePanelComponent } from 'app/shared-ui/side-panel/side-panel.component';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

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
    styleUrls: ['./assessment-dashboard-information.component.scss'],
    imports: [TranslateDirective, PieChartModule, RouterLink, ArtemisTranslatePipe, SidePanelComponent],
})
export class AssessmentDashboardInformationComponent {
    private translateService = inject(TranslateService);

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

    // Re-evaluate language-dependent computeds whenever the active language changes.
    private readonly currentLang = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    // Graph data.
    readonly completedAssessmentsTitle = computed(() => {
        this.currentLang();
        return this.translateService.instant('artemisApp.exerciseAssessmentDashboard.closedAssessments');
    });
    readonly openedAssessmentsTitle = computed(() => {
        this.currentLang();
        return this.translateService.instant('artemisApp.exerciseAssessmentDashboard.openAssessments');
    });
    readonly assessments = computed(() => [
        {
            name: this.openedAssessmentsTitle(),
            value: this.numberOfSubmissions().total - this.totalNumberOfAssessments() / this.numberOfCorrectionRounds(),
        },
        {
            name: this.completedAssessmentsTitle(),
            value: this.totalNumberOfAssessments() / this.numberOfCorrectionRounds(),
        },
    ]);
    readonly customColors = computed(() => [
        {
            name: this.openedAssessmentsTitle(),
            value: GraphColors.RED,
        },
        {
            name: this.completedAssessmentsTitle(),
            value: GraphColors.BLUE,
        },
    ]);

    view: [number, number] = [320, 150];
    legendPosition = LegendPosition.Below;

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
}
