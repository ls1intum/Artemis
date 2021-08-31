import { Component, OnInit, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { BaseChartDirective, Label } from 'ng2-charts';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';

@Component({
    selector: 'jhi-text-exercise-tutor-effort-statistics',
    templateUrl: './tutor-effort-statistics.component.html',
    styleUrls: ['./tutor-effort-statistics.component.scss'],
})
export class TutorEffortStatisticsComponent implements OnInit {
    tutorEfforts: TutorEffort[];
    numberOfSubmissions: number;
    totalTimeSpent: number;
    averageTimeSpent: number;
    currentExerciseId: number;
    currentCourseId: number;
    effortDistribution = new Array<number>(10).fill(0);

    /**
     * Directive to manage the canvas element that renders the chart.
     */
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;
    // datasets: DataSet[] = [];
    /**
     * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
     */
    chartLabels: Label[] = ['0-10', '10-20', '20-30', '30-40', '40-50', '50-60', '60-70', '70-80', '80-90', '90-100', '100-110', '110-120', '120+'];

    /**
     * The similarity distribution is visualized in a bar chart.
     */
    chartType: ChartType = 'bar';

    /**
     * Array of datasets to plot.
     *
     * Only one dataset is necessary to display the similarity distribution.
     */
    chartDataSets: ChartDataSets[] = [
        {
            backgroundColor: 'lightskyblue',
            data: [],
            hoverBackgroundColor: 'dodgerblue',
        },
    ];

    /**
     * When visualizing the similarity distribution, we always want the y-axis to begin at zero.
     * Also, since values on the y-axis will always be integers, we set the step size to 1.
     */
    chartOptions: ChartOptions = {
        title: {
            display: true,
            text: 'Tutor Effort Distribution',
        },
        scales: {
            xAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: 'Minutes',
                    },
                },
            ],
            yAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: 'Tutors',
                    },
                    ticks: {
                        beginAtZero: true,
                        stepSize: 1,
                    },
                },
            ],
        },
    };

    constructor(private textExerciseService: TextExerciseService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.currentExerciseId = Number(params['exerciseId']);
            this.currentCourseId = Number(params['courseId']);
        });
    }

    loadTutorEfforts() {
        this.textExerciseService.calculateTutorEffort(this.currentCourseId, this.currentExerciseId).subscribe(
            (res: TutorEffort[]) => {
                this.tutorEfforts = res!;
                console.log('test', this.tutorEfforts);
                if (!this.tutorEfforts) {
                    return;
                }
                this.numberOfSubmissions = this.tutorEfforts.reduce((n, { numberOfSubmissionsAssessed }) => n + numberOfSubmissionsAssessed, 0);
                this.totalTimeSpent = this.tutorEfforts.reduce((n, { totalTimeSpentMinutes }) => n + totalTimeSpentMinutes, 0);
                const avgTemp = this.numberOfSubmissions / this.totalTimeSpent;
                if (avgTemp) {
                    this.averageTimeSpent = Math.round((avgTemp + Number.EPSILON) * 100) / 100;
                }
                this.distributeEffortToSets();
                this.chartDataSets[0].data = this.effortDistribution;
            },
            (error) => {
                console.error('Error while retrieving tutor effort statistics:', error);
            },
        );
    }

    distributeEffortToSets() {
        this.tutorEfforts.forEach((effort) => {
            const time = Math.floor(effort.totalTimeSpentMinutes / 10);
            if (time > -1 && time < 6) {
                this.effortDistribution[time]++;
            } else if (time >= 12) {
                this.effortDistribution[12]++;
            }
        });
    }
}
