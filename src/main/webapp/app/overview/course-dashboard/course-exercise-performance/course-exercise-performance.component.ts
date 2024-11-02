import { Component, effect, inject, input, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

export interface ExercisePerformance {
    exerciseId: number;
    title: string;
    shortName?: string;
    score?: number;
    averageScore?: number;
}

const YOUR_GRAPH_COLOR = GraphColors.BLUE;
const AVERAGE_GRAPH_COLOR = GraphColors.YELLOW;

@Component({
    selector: 'jhi-course-exercise-performance',
    templateUrl: './course-exercise-performance.component.html',
    styleUrls: ['./course-exercise-performance.component.scss'],
})
export class CourseExercisePerformanceComponent {
    readonly exercisePerformance = input.required<ExercisePerformance[]>();

    private readonly translateService = inject(TranslateService);

    yourScoreLabel: string;
    averageScoreLabel: string;
    ngxData: NgxChartsMultiSeriesDataEntry[];
    ngxColor: Color = {
        name: 'Performance in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [YOUR_GRAPH_COLOR, AVERAGE_GRAPH_COLOR],
    };
    yScaleMax = 100;

    private readonly langChange = toSignal(this.translateService.onLangChange);

    protected readonly YOUR_GRAPH_COLOR = YOUR_GRAPH_COLOR;
    protected readonly AVERAGE_GRAPH_COLOR = AVERAGE_GRAPH_COLOR;

    constructor() {
        // setup chart whenever exercise performance changes
        effect(() => this.setupChart(this.exercisePerformance()));
        // setup chart whenever language changes
        effect(() => {
            const langChanged = this.langChange();
            untracked(() => {
                if (langChanged) {
                    this.setupChart(this.exercisePerformance());
                }
            });
        });
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
    private setupChart(exercisePerformance: ExercisePerformance[]): void {
        this.yourScoreLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel');
        this.averageScoreLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.averageScoreLabel');

        this.ngxData = [
            {
                name: this.yourScoreLabel,
                series: exercisePerformance.map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: round(performance.score || 0, 1), // If the score is undefined, set it to 0
                        extra: {
                            title: performance.title,
                        },
                    };
                }),
            },
            {
                name: this.averageScoreLabel,
                series: exercisePerformance.map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: round(performance.averageScore || 0, 1),
                        extra: {
                            title: performance.title,
                        },
                    };
                }),
            },
        ];

        // Round the maximum score up to the next multiple of 10
        const maxScore = Math.max(...this.ngxData.flatMap((data) => data.series.map((series) => series.value)));
        this.yScaleMax = Math.max(100, Math.ceil(maxScore / 10) * 10);
    }
}
