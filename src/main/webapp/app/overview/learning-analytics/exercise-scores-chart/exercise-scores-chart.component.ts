import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import * as Chart from 'chart.js';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Color, Label } from 'ng2-charts';
import { ExerciseScoresDTO } from 'app/overview/learning-analytics/exercise-scores-chart/exercise-scores-dto.model';
import * as moment from 'moment';
import * as _ from 'lodash';

@Component({
    selector: 'jhi-exercise-scores-chart',
    templateUrl: './exercise-scores-chart.component.html',
    styleUrls: ['./exercise-scores-chart.component.scss'],
})
export class ExerciseScoresChartComponent implements OnInit, AfterViewInit {
    public exerciseScores: ExerciseScoresDTO[] = [];

    @ViewChild(BaseChartDirective)
    chartDirective: BaseChartDirective;
    chartInstance: Chart;
    @ViewChild('chartDiv')
    chartDiv: ElementRef;
    public lineChartData: ChartDataSets[] = [
        {
            fill: false,
            data: [],
            label: 'Your Score',
            pointRadius: 8,
            pointStyle: 'circle',
            borderWidth: 5,
            lineTension: 0,
        },
        {
            fill: false,
            data: [],
            label: 'Average Score',
            pointRadius: 8,
            pointStyle: 'rect',
            borderWidth: 5,
            lineTension: 0,
            borderDash: [10, 5],
        },
    ];
    public lineChartLabels: Label[] = this.exerciseScores.map((exerciseScoreDTO) => exerciseScoreDTO.exerciseTitle);
    public lineChartOptions: ChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        title: {
            display: true,
            text: 'Scores',
            position: 'top',
            fontSize: 20,
        },
        legend: {
            position: 'bottom',
        },
        scales: {
            yAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: 'Score (%)',
                    },
                    ticks: {
                        suggestedMax: 100,
                        suggestedMin: 0,
                        beginAtZero: true,
                        precision: 0,
                    },
                },
            ],
            xAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: 'Exercises',
                    },
                    ticks: {
                        autoSkip: false,
                        maxRotation: 45,
                        minRotation: 45,
                        callback(exerciseTitle: string) {
                            if (exerciseTitle.length > 20) {
                                // shorten exercise title if too long (will be displayed in full in tooltip)
                                return exerciseTitle.substr(0, 20) + '...';
                            } else {
                                return exerciseTitle;
                            }
                        },
                    },
                },
            ],
        },
    };
    public lineChartColors: Color[] = [
        {
            borderColor: 'skyBlue',
            backgroundColor: 'skyBlue',
        },
        {
            borderColor: 'salmon',
            backgroundColor: 'salmon',
        },
    ];
    public lineChartLegend = true;
    public lineChartType: ChartType = 'line';
    public lineChartPlugins = [];

    constructor() {}

    ngOnInit() {}

    addData(chart: Chart, exerciseScoresDTOS: ExerciseScoresDTO[]) {
        for (const exerciseScoreDTO of exerciseScoresDTOS) {
            chart.data.labels!.push(exerciseScoreDTO.exerciseTitle);
            chart.data.datasets![0].data!.push(exerciseScoreDTO.studentScore);
            chart.data.datasets![1].data!.push(exerciseScoreDTO.averageScore);
        }
        chart.update();
    }

    loadData() {
        // ToDo sort date first by release date and then by id
        this.exerciseScores.push(new ExerciseScoresDTO(1, 'This is a really long exercise title that is far too long to be displayed', 50, 79, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(2, 'test2', 30, 100, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(3, 'test3', 0, 200, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(4, 'test4', 35, 180, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(5, 'test5', 11, 34, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(6, 'test6', 0, 0, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(7, 'This is a really long exercise title that is far too long to be displayed', 50, 79, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(8, 'test2', 30, 100, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(9, 'test3', 0, 200, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(10, 'test4', 35, 180, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(11, 'test5', 11, 34, moment()));
        this.exerciseScores.push(new ExerciseScoresDTO(12, 'test6', 0, 0, moment()));

        const sortedExerciseScores = _.sortBy(this.exerciseScores, ['releaseDate', 'exerciseId']);

        this.addData(this.chartInstance, sortedExerciseScores);
    }

    ngAfterViewInit() {
        this.chartInstance = this.chartDirective.chart;
        this.loadData();
        const chartWidth = 500 + 100 * this.exerciseScores.length;
        this.chartDiv.nativeElement.setAttribute('style', `width: ${chartWidth}px`);
    }
}
