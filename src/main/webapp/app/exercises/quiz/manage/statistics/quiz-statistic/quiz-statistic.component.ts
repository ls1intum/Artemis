import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { Chart, ChartAnimationOptions, ChartOptions, LinearTickOptions } from 'chart.js';
import { Subscription } from 'rxjs/Subscription';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { BaseChartDirective } from 'ng2-charts';

/**
 * this interface is adapted from chart.js
 */
export interface DataSet {
    data: number[];
    backgroundColor: string[];
}

export interface ChartElement {
    chart: Chart;
}

// this code is reused in 4 different statistic components
export function createOptions(dataSetProvider: DataSetProvider, max: number, stepSize: number, xLabel: string, yLabel: string): ChartOptions {
    return {
        layout: {
            padding: {
                left: 0,
                right: 0,
                top: 0,
                bottom: 30,
            },
        },
        legend: {
            display: false,
        },
        title: {
            display: false,
            text: '',
            position: 'top',
            fontSize: 16,
            padding: 20,
        },
        tooltips: {
            enabled: false,
        },
        scales: {
            yAxes: [
                {
                    display: true,
                    scaleLabel: {
                        labelString: xLabel,
                        display: true,
                    },
                    ticks: {
                        beginAtZero: true,
                        stepSize,
                        max,
                        min: 0,
                    } as LinearTickOptions,
                },
            ],
            xAxes: [
                {
                    scaleLabel: {
                        labelString: yLabel,
                        display: true,
                    },
                },
            ],
        },
        hover: { animationDuration: 0 },
        animation: createAnimation(dataSetProvider),
    };
}

// this code is reused in 4 different statistic components
export function createAnimation(dataSetProvider: DataSetProvider): ChartAnimationOptions {
    return {
        duration: 500,
        onComplete: (chartElement: ChartElement) => {
            const chartInstance = chartElement.chart;
            const ctx = chartInstance.ctx!;

            ctx.font = Chart.helpers.fontString(Chart.defaults.global.defaultFontSize, Chart.defaults.global.defaultFontStyle, Chart.defaults.global.defaultFontFamily);
            ctx.textAlign = 'center';
            ctx.textBaseline = 'bottom';
            const participants = dataSetProvider.getParticipants();

            dataSetProvider.getDataSets().forEach((dataset: DataSet, datasetIndex: number) => {
                const meta = chartInstance.getDatasetMeta(datasetIndex);
                meta.data.forEach((bar: any, dataIndex: number) => {
                    const data = (Math.round(dataset.data[dataIndex] * 100) / 100).toString();
                    const dataPercentage = Math.round((dataset.data[dataIndex] / participants) * 1000) / 10 || 0;
                    ctx.fillText(data, bar._model.x, bar._model.y - 20);
                    ctx.fillText(`(${dataPercentage}%)`, bar._model.x, bar._model.y - 5);
                });
            });
        },
    };
}

export interface DataSetProvider {
    getDataSets(): DataSet[];
    getParticipants(): number;

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    loadDataInDiagram(): void;
}

export function calculateHeightOfChart(dataSetProvider: DataSetProvider) {
    const data = dataSetProvider.getDataSets().map((dataset) => {
        return dataset.data;
    });
    const flattened = ([] as number[]).concat(...data);
    const max = Math.max(...flattened);
    // we provide 300 as buffer at the top to display labels
    const height = Math.ceil((max + 1) / 10) * 10;
    if (height < 10) {
        return height + 3;
    } else if (height < 1000) {
        // add 25%, round to the next 10
        return Math.ceil(height * 0.125) * 10;
    } else {
        // add 25%, round to the next 100
        return Math.ceil(height * 0.0125) * 100;
    }
}

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html',
})
export class QuizStatisticComponent implements OnInit, OnDestroy, DataSetProvider {
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    quizExercise: QuizExercise;
    private sub: Subscription;

    labels: string[] = [];
    data: number[] = [];
    colors: string[] = [];
    chartType = 'bar';
    datasets: DataSet[] = [];

    label: string[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    backgroundColor: string[] = [];
    ratedAverage: number;
    unratedAverage: number;

    maxScore: number;
    rated = true;
    participants: number;
    websocketChannelForData: string;

    // options for chart.js style
    options: ChartOptions;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private quizStatisticUtil: QuizStatisticUtil,
        private jhiWebsocketService: JhiWebsocketService,
    ) {}

    loadDataInDiagram(): void {
        throw new Error('Method not implemented.');
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe((params) => {
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA])) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<QuizExercise>) => {
                    this.loadQuizSuccess(res.body!);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['exerciseId'];
            this.jhiWebsocketService.subscribe(this.websocketChannelForData);

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe(() => {
                if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA])) {
                    this.quizExerciseService.find(params['exerciseId']).subscribe((res) => {
                        this.loadQuizSuccess(res.body!);
                    });
                }
            });
        });
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    getDataSets() {
        return this.datasets;
    }

    getParticipants() {
        return this.participants;
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which the this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA])) {
            this.router.navigate(['/courses']);
        }
        this.quizExercise = quiz;
        this.maxScore = this.calculateMaxScore();
        this.loadData();
    }

    /**
     * calculate the maximal  possible Score for the quiz
     *
     * @return (int): sum over the Scores of all questions
     */
    calculateMaxScore() {
        let result = 0;

        if (this.quizExercise.quizQuestions) {
            this.quizExercise.quizQuestions.forEach(function (question) {
                result = result + question.points!;
            });
        } else {
            result = this.quizExercise.maxPoints!;
        }
        return result;
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        // reset old data
        this.label = [];
        this.backgroundColor = [];
        this.ratedData = [];
        this.unratedData = [];
        this.ratedAverage = 0;
        this.unratedAverage = 0;

        // set data based on the CorrectCounters in the QuestionStatistics
        for (let i = 0; i < this.quizExercise.quizQuestions!.length; i++) {
            const question = this.quizExercise.quizQuestions![i];
            const statistic = question.quizQuestionStatistic!;
            const ratedCounter = statistic.ratedCorrectCounter!;
            const unratedCounter = statistic.unRatedCorrectCounter!;
            this.label.push(i + 1 + '.');
            this.backgroundColor.push('#5bc0de');
            this.ratedData.push(ratedCounter);
            this.unratedData.push(unratedCounter);
            this.ratedAverage = this.ratedAverage + ratedCounter * question.points!;
            this.unratedAverage = this.unratedAverage + unratedCounter * question.points!;
        }

        // set Background for invalid questions = grey
        for (let i = 0; i < this.quizExercise.quizQuestions!.length; i++) {
            if (this.quizExercise.quizQuestions![i].invalid) {
                this.backgroundColor[i] = '#949494';
            }
        }

        // add data for the last bar (Average)
        this.backgroundColor.push('#1e3368');
        this.ratedData.push(this.ratedAverage / this.maxScore);
        this.unratedData.push(this.unratedAverage / this.maxScore);

        // load data into the chart
        this.labels = this.label;
        this.colors = this.backgroundColor;

        // add Text for last label based on the language
        const lastLabel = this.translateService.instant('showStatistic.quizStatistic.average');
        this.label.push(lastLabel);

        // if this.rated == true  -> load the rated data
        if (this.rated) {
            this.participants = this.quizExercise.quizPointStatistic!.participantsRated!;
            this.data = this.ratedData;
        } else {
            // load the unrated data
            this.participants = this.quizExercise.quizPointStatistic!.participantsUnrated!;
            this.data = this.unratedData;
        }

        this.updateChart();
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the amount of  participants
     *  2. change the bar-Data
     */
    switchRated() {
        if (this.rated) {
            // load unrated Data
            this.data = this.unratedData;
            this.participants = this.quizExercise.quizPointStatistic!.participantsUnrated!;
            this.rated = false;
        } else {
            // load rated Data
            this.data = this.ratedData;
            this.participants = this.quizExercise.quizPointStatistic!.participantsRated!;
            this.rated = true;
        }

        this.updateChart();
    }

    /**
     * updates the chart by setting the data set, re-calculating the height and calling update on the chart view child
     */
    updateChart() {
        this.datasets = [{ data: this.data, backgroundColor: this.colors }];
        // recalculate the height of the chart because rated/unrated might have changed or new results might have appeared
        const height = calculateHeightOfChart(this);
        // add Axes-labels based on selected language
        const xLabel = this.translateService.instant('showStatistic.quizStatistic.xAxes');
        const yLabel = this.translateService.instant('showStatistic.quizStatistic.yAxes');
        this.options = createOptions(this, height, height / 5, xLabel, yLabel);
        if (this.chart) {
            this.chart.update(0);
        }
    }
}
