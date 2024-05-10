import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

export interface ExerciseLateness {
    exerciseId: number;
    title: string;
    shortName?: string;
    relativeLatestSubmission: number;
    relativeAverageLatestSubmission: number;
}

@Component({
    selector: 'jhi-course-exercise-lateness',
    templateUrl: './course-exercise-lateness.component.html',
    styleUrl: './course-exercise-lateness.component.scss',
})
export class CourseExerciseLatenessComponent implements OnInit, OnChanges {
    @Input() exerciseLateness: ExerciseLateness[] = [];

    yourLatenessLabel: string;
    averageLatenessLabel: string;
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
     * Sets up the chart for given exercise lateness
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
                        value: lateness.relativeLatestSubmission,
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
                        value: lateness.relativeAverageLatestSubmission,
                        extra: {
                            title: lateness.title,
                        },
                    };
                }),
            },
        ];

        const maxRelativeTime = Math.max(...this.exerciseLateness.flatMap((lateness) => [lateness.relativeLatestSubmission, lateness.relativeAverageLatestSubmission]));
        this.yScaleMax = Math.max(100, Math.ceil(maxRelativeTime / 10) * 10);
    }
}
