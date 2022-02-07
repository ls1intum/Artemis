import { Component, Input, OnInit } from '@angular/core';
import { GraphColors, Graphs, SpanType } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { faArrowLeft, faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Router } from '@angular/router';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

interface ExerciseStatisticsEntry extends NgxChartsSingleSeriesDataEntry {
    exerciseType: ExerciseType;
    exerciseId: number;
}

@Component({
    selector: 'jhi-statistics-average-score-graph',
    templateUrl: './statistics-average-score-graph.component.html',
})
export class StatisticsAverageScoreGraphComponent implements OnInit {
    @Input()
    exerciseAverageScores: CourseManagementStatisticsModel[];
    @Input()
    courseAverage: number;
    @Input()
    courseId: number;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Data
    barChartLabels: string[] = [];
    // ngx
    ngxData: ExerciseStatisticsEntry[] = [];
    ngxColor = {
        name: 'Course statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;

    // Icons
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    constructor(private router: Router) {}

    ngOnInit(): void {
        this.initializeChart();
    }

    private initializeChart(): void {
        this.barChartLabels = this.exerciseAverageScores.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.exerciseName);
        this.ngxData = this.exerciseAverageScores.slice(this.currentPeriod, 10 + this.currentPeriod).map(
            (exercise, index) =>
                ({
                    name: this.barChartLabels[index],
                    value: exercise.averageScore,
                    exerciseType: exercise.exerciseType,
                    exerciseId: exercise.exerciseId,
                } as ExerciseStatisticsEntry),
        );
        this.ngxColor.domain = this.ngxData.map((exercise) => this.determineColor(exercise.value));
    }

    // handles arrow clicks and updates the exercises which are shown, forward is boolean since it is either forward or backward
    public switchTimeSpan(forward: boolean): void {
        this.currentPeriod += forward ? 1 : -1;
        this.initializeChart();
    }

    /**
     * Determines the color of the bar given the score
     * @param score that is represented by the bar
     * @returns string rgba representation of the color
     * @private
     */
    private determineColor(score: number): string {
        if (score >= 90) {
            return GraphColors.GREEN;
        } else if (score <= 25) {
            return GraphColors.RED;
        } else {
            return GraphColors.DARK_BLUE;
        }
    }

    /**
     * Handles the click event on one of the bars and navigates to the corresponding exercise statistics page
     * @param event the event that is passed by the framework
     */
    onSelect(event: any): void {
        const dataEntry = this.determineChartEntry(event.name, event.value);

        // a workaround in order to prevent false navigation. If more than one exercise is matching, no routing is done
        if (dataEntry) {
            const route = ['course-management', this.courseId, '', dataEntry.exerciseId, 'exercise-statistics'];
            let type = dataEntry.exerciseType.toLowerCase();
            if (type === ExerciseType.QUIZ) {
                route[4] = 'quiz-point-statistic';
            } else if (type === 'file_upload') {
                type = 'file-upload';
            }
            route[2] = type + '-exercises';
            this.router.navigate(route);
        }
    }

    /**
     * Determines the entry in the chart for a mouse event
     * @param name name of the exercise
     * @param value average score of the exercise
     * @private
     */
    private determineChartEntry(name: string, value: number): ExerciseStatisticsEntry | undefined {
        let counter = 0;
        let result;
        /*
         * The emitted event only contains the name and the average score of the exercise. Using those values to determine the chart entry
         * is not an ideal solution as this pair is not necessarily unique.
         * In practice they most likely are unique, though. Not being able to find the entry in this edge case therefore has negligible impact.
         */
        this.ngxData.forEach((exercise) => {
            if (exercise.name === name && exercise.value === value) {
                counter++;
                result = exercise;
            }
        });
        // if more than one exercise match, we do not navigate
        return counter === 1 ? result : undefined;
    }

    /**
     * Converts the exercise type contained in the ExerciseStatisticEntries for the tooltip in order to make it translatable
     * @param name the name of the exercise
     * @param value the average score of the exercise
     */
    convertTypeForTooltip(name: string, value: number): string {
        const entry = this.determineChartEntry(name, value);
        if (entry) {
            const type = entry.exerciseType.toLowerCase();
            if (type === 'file_upload') {
                return 'file-upload';
            }
            return type;
        } else {
            // if the name and value is not unique, we cannot determine the type
            return '';
        }
    }
}
