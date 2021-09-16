import { Component, OnInit, ViewChild } from '@angular/core';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { BaseChartDirective, Label } from 'ng2-charts';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';

@Component({
    selector: 'jhi-text-exercise-tutor-effort-statistics',
    templateUrl: './tutor-effort-statistics.component.html',
    styleUrls: ['./tutor-effort-statistics.component.scss'],
})
export class TutorEffortStatisticsComponent implements OnInit {
    tutorEfforts: TutorEffort[] = [];
    numberOfSubmissions: number;
    totalTimeSpent: number;
    averageTimeSpent: number;
    currentExerciseId: number;
    currentCourseId: number;
    numberOfTutorsInvolvedInCourse: number;
    effortDistribution: number[];

    /**
     * Directive to manage the canvas element that renders the chart.
     */
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    // Distance value representing step difference between chartLabel entries, i.e:. 1-10, 10-20
    bucketSize = 10;

    /**
     * The labels of the chart are fixed and represent the 13 intervals we group the tutors into.
     */
    chartLabels: Label[] = ['0-10', '10-20', '20-30', '30-40', '40-50', '50-60', '60-70', '70-80', '80-90', '90-100', '100-110', '110-120', '120+'];

    /**
     * The type of the chart.
     */
    chartType: ChartType = 'bar';

    /**
     * Array of datasets to plot.
     */
    chartDataSets: ChartDataSets[] = [
        {
            backgroundColor: 'lightskyblue',
            data: [],
            hoverBackgroundColor: 'dodgerblue',
        },
    ];

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

    constructor(private textExerciseService: TextExerciseService, private textAssessmentService: TextAssessmentService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.currentExerciseId = Number(params['exerciseId']);
            this.currentCourseId = Number(params['courseId']);
        });
        this.loadTutorEfforts();
    }

    loadTutorEfforts() {
        this.textExerciseService.calculateTutorEffort(this.currentExerciseId, this.currentCourseId).subscribe((tutorEffortResponse: TutorEffort[]) => {
            this.handleTutorEffortResponse(tutorEffortResponse);
        });
        this.loadNumberOfTutorsInvolved();
    }

    /**
     * Handler function to handle input data coming from service call.
     * Separation enables better testing
     * @param tutorEffortData - data to handle
     */
    handleTutorEffortResponse(tutorEffortData: TutorEffort[]) {
        this.tutorEfforts = tutorEffortData;
        if (!this.tutorEfforts) {
            return;
        }
        this.numberOfSubmissions = this.tutorEfforts.reduce((n, { numberOfSubmissionsAssessed }) => n + numberOfSubmissionsAssessed, 0);
        this.totalTimeSpent = this.tutorEfforts.reduce((n, { totalTimeSpentMinutes }) => n + totalTimeSpentMinutes, 0);
        const avgTemp = this.totalTimeSpent === 0 ? 0 : this.numberOfSubmissions / this.totalTimeSpent;
        this.averageTimeSpent = avgTemp ? Math.round((avgTemp + Number.EPSILON) * 100) / 100 : 0;
        this.distributeEffortToSets();
        this.chartDataSets[0].data = this.effortDistribution;
    }

    loadNumberOfTutorsInvolved() {
        this.textAssessmentService.getNumberOfTutorsInvolvedInAssessment(this.currentCourseId, this.currentExerciseId).subscribe((response: number) => {
            this.numberOfTutorsInvolvedInCourse = response;
        });
    }

    /**
     * Tutor Effort is distributed among the effortDistribution entries with each entry representing
     * a corresponding index in the chartLables field.
     * chartLabels.["0-10"] - corresponds to effortDistribution[0]
     * chartLabels.["10-20"] - corresponds to effortDistribution[1]
     * and so on. chartlabels is divided in steps of length 10, which is why division/10 and floor function is used.
     */
    distributeEffortToSets() {
        this.effortDistribution = new Array<number>(this.chartLabels.length).fill(0);
        this.tutorEfforts.forEach((effort) => {
            const BUCKET_LAST_INDEX = this.chartLabels.length - 1;
            const BUCKET_POSITION = effort.totalTimeSpentMinutes / this.bucketSize;
            // the element will either be distributed in one of first 12 elements (chartLabels.length)
            // or the last element if the time passed is larger than 120 (i.e.: chartLabels[12] = 120+)
            const BUCKET_INDEX = Math.min(Math.floor(BUCKET_POSITION), BUCKET_LAST_INDEX);
            this.effortDistribution[BUCKET_INDEX]++;
        });
    }
}
