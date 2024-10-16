import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';
import { Subscription } from 'rxjs';

export interface ExerciseLateness {
    exerciseId: number;
    title: string;
    shortName?: string;
    relativeLatestSubmission?: number;
    relativeAverageLatestSubmission?: number;
}

const YOUR_GRAPH_COLOR = GraphColors.BLUE;
const AVERAGE_GRAPH_COLOR = GraphColors.YELLOW;

@Component({
    selector: 'jhi-course-exercise-lateness',
    templateUrl: './course-exercise-lateness.component.html',
    styleUrls: ['./course-exercise-lateness.component.scss'],
})
export class CourseExerciseLatenessComponent implements OnInit, OnChanges, OnDestroy {
    private translateService = inject(TranslateService);

    @Input() exerciseLateness: ExerciseLateness[] = [];

    yourLatenessLabel: string;
    averageLatenessLabel: string;
    ngxData: NgxChartsMultiSeriesDataEntry[];

    ngxColor: Color = {
        name: 'Lateness in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [YOUR_GRAPH_COLOR, AVERAGE_GRAPH_COLOR],
    };
    yScaleMax = 100;

    private translateServiceSubscription: Subscription;

    protected readonly YOUR_GRAPH_COLOR = YOUR_GRAPH_COLOR;
    protected readonly AVERAGE_GRAPH_COLOR = AVERAGE_GRAPH_COLOR;

    constructor() {
        this.translateServiceSubscription = this.translateService.onLangChange.subscribe(() => {
            this.setupChart();
        });
    }

    ngOnInit(): void {
        this.setupChart();
    }

    ngOnDestroy(): void {
        this.translateServiceSubscription.unsubscribe();
    }

    ngOnChanges(): void {
        this.setupChart();
    }

    /**
     * This getter checks if there is data available for the chart.
     * It checks if `ngxData` is defined, if it has at least one entry, and if at least one of those entries has a non-empty `series` array.
     * @returns {boolean} - Returns true if data is available for the chart, false otherwise.
     */
    get isDataAvailable(): boolean {
        return this.ngxData && this.ngxData.length > 0 && this.ngxData.some((data) => data.series.length > 0);
    }

    /**
     * This method is responsible for setting up the chart that displays the lateness of the exercises.
     * It translates the labels for the chart, prepares the data for the chart, and calculates the maximum value for the y-axis.
     */
    private setupChart(): void {
        this.yourLatenessLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.yourLatenessLabel');
        this.averageLatenessLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.averageLatenessLabel');

        this.ngxData = [
            {
                name: this.yourLatenessLabel,
                series: this.exerciseLateness.map((lateness) => {
                    return {
                        name: lateness.shortName?.toUpperCase() || lateness.title,
                        value: round(lateness.relativeLatestSubmission || 100, 1), // If there is no data, we assume the submission is late
                        extra: {
                            title: lateness.title,
                        },
                    };
                }),
            },
            {
                name: this.averageLatenessLabel,
                series: this.exerciseLateness.map((lateness) => {
                    return {
                        name: lateness.shortName?.toUpperCase() || lateness.title,
                        value: round(lateness.relativeAverageLatestSubmission || 100, 1),
                        extra: {
                            title: lateness.title,
                        },
                    };
                }),
            },
        ];

        // Round the maximum score up to the next multiple of 10
        const maxRelativeTime = Math.max(...this.ngxData.flatMap((data) => data.series.map((series) => series.value)));
        this.yScaleMax = Math.max(100, Math.ceil(maxRelativeTime / 10) * 10);
    }
}
