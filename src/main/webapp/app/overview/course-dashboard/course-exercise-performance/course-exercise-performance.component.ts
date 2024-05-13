import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

export interface ExercisePerformance {
    exerciseId: number;
    title: string;
    shortName?: string;
    score: number;
    averageScore: number;
}

@Component({
    selector: 'jhi-course-exercise-performance',
    templateUrl: './course-exercise-performance.component.html',
    styleUrl: './course-exercise-performance.component.scss',
})
export class CourseExercisePerformanceComponent implements OnInit, OnChanges {
    @Input() exercisePerformance: ExercisePerformance[] = [];

    yourScoreLabel: string;
    averageScoreLabel: string;
    ngxData: NgxChartsMultiSeriesDataEntry[];
    ngxColor: Color = {
        name: 'Performance in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.BLUE, GraphColors.YELLOW],
    };
    yScaleMax = 100;

    readonly round = round;
    readonly Math = Math;

    constructor(private translateService: TranslateService) {
        this.translateService.onLangChange.subscribe(() => {
            this.setupChart();
        });
    }

    ngOnInit(): void {
        this.setupChart();
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
     * This method is responsible for setting up the chart that displays the performance of the exercises.
     * It translates the labels for the chart, prepares the data for the chart, and calculates the maximum value for the y-axis.
     */
    private setupChart(): void {
        this.yourScoreLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel');
        this.averageScoreLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.averageScoreLabel');

        this.ngxData = [
            {
                name: this.yourScoreLabel,
                series: this.exercisePerformance.map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: performance.score,
                        extra: {
                            title: performance.title,
                        },
                    };
                }),
            },
            {
                name: this.averageScoreLabel,
                series: this.exercisePerformance.map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: performance.averageScore,
                        extra: {
                            title: performance.title,
                        },
                    };
                }),
            },
        ];

        const maxScore = Math.max(...this.exercisePerformance.flatMap((performance) => [performance.score, performance.averageScore]));
        this.yScaleMax = Math.max(100, Math.ceil(maxScore / 10) * 10);
    }
}
