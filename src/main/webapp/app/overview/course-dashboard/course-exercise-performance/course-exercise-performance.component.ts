import { Component, Input, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

export interface ExercisePerformance {
    exerciseId: number;
    title: string;
    endDate: string;
    maxPoints: number;
    studentPoints: number;
    courseAveragePoints: number;
}

@Component({
    selector: 'jhi-course-exercise-performance',
    templateUrl: './course-exercise-performance.component.html',
    styleUrl: './course-exercise-performance.component.scss',
})
export class CourseExercisePerformanceComponent implements OnInit {
    @Input() exercisePerformances: ExercisePerformance[] = [];

    yourScoreLabel: string;
    averageScoreLabel: string;
    ngxData: NgxChartsMultiSeriesDataEntry[];
    ngxColor: Color = {
        name: 'Performance in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.BLUE, GraphColors.YELLOW],
    };

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

    /**
     * Sets up the chart for given exercise performances
     */
    private setupChart(): void {
        this.yourScoreLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel');
        this.averageScoreLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.averageScoreLabel');

        this.ngxData = [
            {
                name: this.yourScoreLabel,
                series: this.exercisePerformances.map((performance) => {
                    return {
                        name: performance.title,
                        value: (performance.studentPoints / performance.maxPoints) * 100,
                    };
                }),
            },
            {
                name: this.averageScoreLabel,
                series: this.exercisePerformances.map((performance) => {
                    return {
                        name: performance.title,
                        value: (performance.courseAveragePoints / performance.maxPoints) * 100,
                    };
                }),
            },
        ];
    }
}
