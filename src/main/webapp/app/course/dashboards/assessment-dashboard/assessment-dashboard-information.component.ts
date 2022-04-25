import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { LegendPosition } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';
import { getGraphColorForTheme, GraphColors } from 'app/entities/statistics.model';
import { ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';

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
export class AssessmentDashboardInformationComponent implements OnInit, OnChanges, OnDestroy {
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

    complaintsLink: any[];
    moreFeedbackRequestsLink: any[];
    assessmentLocksLink: any[];
    ratingsLink: any[];

    themeSubscription: Subscription;

    constructor(private translateService: TranslateService, private themeService: ThemeService) {}

    ngOnInit(): void {
        this.setup();
        this.translateService.onLangChange.subscribe(() => {
            this.setupGraph();
        });

        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe(
            (theme) =>
                (this.customColors = [
                    {
                        name: this.openedAssessmentsTitle,
                        value: getGraphColorForTheme(theme, GraphColors.RED),
                    },
                    {
                        name: this.completedAssessmentsTitle,
                        value: getGraphColorForTheme(theme, GraphColors.BLUE),
                    },
                ]),
        );
    }

    ngOnChanges(): void {
        this.setup();
    }

    ngOnDestroy(): void {
        this.themeSubscription?.unsubscribe();
    }

    setup() {
        this.setupLinks();
        this.setupGraph();
    }

    setupLinks() {
        const examRouteIfNeeded = this.isExamMode ? ['exams', this.examId!] : [];

        this.complaintsLink = ['/course-management', this.courseId].concat(examRouteIfNeeded).concat(['complaints']);
        this.moreFeedbackRequestsLink = ['/course-management', this.courseId].concat(examRouteIfNeeded).concat(['more-feedback-requests']);
        this.assessmentLocksLink = ['/course-management', this.courseId].concat(examRouteIfNeeded).concat(['assessment-locks']);
        this.ratingsLink = ['/course-management', this.courseId, 'ratings'];
    }

    setupGraph() {
        this.completedAssessmentsTitle = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.closedAssessments');
        this.openedAssessmentsTitle = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.openAssessments');

        this.assessments = [
            {
                name: this.openedAssessmentsTitle,
                value: this.numberOfSubmissions.total - this.totalNumberOfAssessments.total / this.numberOfCorrectionRounds,
            },
            {
                name: this.completedAssessmentsTitle,
                value: this.totalNumberOfAssessments.total / this.numberOfCorrectionRounds,
            },
        ];
    }
}
