import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

export interface ExercisePerformance {
    exerciseId: number;
    title: string;
    shortName?: string;
    dueDate: string;
    maxPoints: number;
    releaseDate: string;
    studentPoints: number;
    courseAveragePoints: number;
}

@Component({
    selector: 'jhi-course-exercise-performance',
    templateUrl: './course-exercise-performance.component.html',
    styleUrl: './course-exercise-performance.component.scss',
})
export class CourseExercisePerformanceComponent implements OnInit, OnChanges {
    @Input() exercisePerformances: ExercisePerformance[] = [];

    ngxData: NgxChartsSingleSeriesDataEntry[] = [];
    ngxColor: Color = {
        name: 'Exercise Performance',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.DARK_BLUE],
    };

    constructor(private translateService: TranslateService) {}

    ngOnInit(): void {
        this.createChart();
    }

    ngOnChanges() {
        this.createChart();
    }

    formatYAxisTick(value: number): string {
        return `${value}%`;
    }

    /**
     * Sets up the chart for given exercise performances
     */
    private createChart(): void {
        this.setupChartColoring();
        this.ngxData = this.exercisePerformances.map((performance) => {
            return {
                name: performance.shortName || `${performance.exerciseId}`,
                value: (performance.studentPoints / performance.maxPoints) * 100,
            };
        });
    }

    /**
     * Sets up the bar coloring
     * If exercise is 100% correct, the bar is green
     * If exercise is above 1/3 correct, the bar is yellow
     * Otherwise the bar is red
     */
    private setupChartColoring(): void {
        this.ngxColor.domain = [];
        for (const performance of this.exercisePerformances) {
            if (performance.studentPoints === performance.maxPoints) {
                this.ngxColor.domain.push(GraphColors.GREEN);
            } else if (performance.studentPoints / performance.maxPoints > 0.25) {
                this.ngxColor.domain.push(GraphColors.YELLOW);
            } else {
                this.ngxColor.domain.push(GraphColors.RED);
            }
        }
    }

    protected readonly round = round;
}
