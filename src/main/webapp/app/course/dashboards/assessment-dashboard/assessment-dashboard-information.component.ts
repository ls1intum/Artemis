import { Component, Input, OnInit } from '@angular/core';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { LegendPosition } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';

export class AssessmentDashboardInformationEntry {
    constructor(public total: number, public tutor: number, public done?: number) {}

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
})
export class AssessmentDashboardInformationComponent implements OnInit {
    @Input() isExamMode: boolean;
    @Input() courseId: number;
    @Input() examId?: number;
    @Input() tutorId: number;
    @Input() isAtLeastInstructor: boolean;

    @Input() complaintsEnabled: boolean;
    @Input() feedbackRequestEnabled: boolean;

    @Input() numberOfCorrectionRounds: number;
    @Input() numberOfAssessmentsOfCorrectionRounds: DueDateStat[];

    @Input() totalNumberOfAssessments: DueDateStat;
    @Input() numberOfSubmissions: DueDateStat;
    @Input() numberOfTutorAssessments: number;
    @Input() totalAssessmentPercentage: number;

    @Input() complaints: AssessmentDashboardInformationEntry;
    @Input() moreFeedbackRequests: AssessmentDashboardInformationEntry;
    @Input() assessmentLocks: AssessmentDashboardInformationEntry;
    @Input() ratings: AssessmentDashboardInformationEntry;

    // Graph data.
    completedAssessmentsTitle: string;
    openedAssessmentsTitle: string;
    assessments: any[];
    customColors: any[];
    view: [number, number] = [320, 150];
    legendPosition = LegendPosition.Below;

    constructor(private translateService: TranslateService) {}

    ngOnInit(): void {
        this.setupGraph();

        this.translateService.onLangChange.subscribe(() => {
            this.setupGraph();
        });
    }

    setupGraph() {
        this.completedAssessmentsTitle = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.closedAssessments');
        this.openedAssessmentsTitle = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.openAssessments');

        this.customColors = [
            {
                name: this.completedAssessmentsTitle,
                value: '#98C7EF',
            },
            {
                name: this.openedAssessmentsTitle,
                value: '#F4A7B6',
            },
        ];

        this.assessments = [
            {
                name: this.completedAssessmentsTitle,
                value: this.totalNumberOfAssessments.total,
            },
            {
                name: this.openedAssessmentsTitle,
                value: this.numberOfSubmissions.total - this.totalNumberOfAssessments.total,
            },
        ];
    }
}
