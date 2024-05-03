import { Component, Input, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

export interface ExerciseLateness {
    exerciseId: number;
    title: string;
    startDate: string;
    endDate: string;
    studentCompletionDate: string;
    courseAverageCompletionDate: string;
}

@Component({
    selector: 'jhi-course-exercise-lateness',
    templateUrl: './course-exercise-lateness.component.html',
    styleUrl: './course-exercise-lateness.component.scss',
})
export class CourseExerciseLatenessComponent implements OnInit {
    @Input() exerciseLateness: ExerciseLateness[] = [];

    yourTimingLabel: string;
    averageTimingLabel: string;
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
     * Sets up the chart for given exercise lateness
     */
    private setupChart(): void {
        this.yourTimingLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.yourTimingLabel');
        this.averageTimingLabel = this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.averageTimingLabel');

        this.ngxData = [
            {
                name: this.yourTimingLabel,
                series: this.exerciseLateness.map((lateness) => {
                    const completionTime = new Date(lateness.studentCompletionDate).getTime();
                    const startTime = new Date(lateness.startDate).getTime();
                    const endTime = new Date(lateness.endDate).getTime();

                    // Log if out of bounds
                    if (completionTime < startTime) {
                        console.log('Completion time is before start time');
                    } else if (completionTime > endTime) {
                        console.log('Completion time is after end time');
                    }

                    return {
                        name: lateness.title,
                        value: ((endTime - Math.min(completionTime, endTime)) / (endTime - startTime)) * 100,
                    };
                }),
            },
            {
                name: this.averageTimingLabel,
                series: this.exerciseLateness.map((lateness) => {
                    const avgCompletionTime = new Date(lateness.courseAverageCompletionDate).getTime();
                    const startTime = new Date(lateness.startDate).getTime();
                    const endTime = new Date(lateness.endDate).getTime();
                    return {
                        name: lateness.title,
                        value: ((endTime - Math.min(avgCompletionTime, endTime)) / (endTime - startTime)) * 100,
                    };
                }),
            },
        ];
    }
}
