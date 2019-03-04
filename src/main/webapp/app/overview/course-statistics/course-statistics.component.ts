import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';

import {
    ABSOLUTE_SCORE,
    MAX_SCORE,
    RELATIVE_SCORE,
    Course,
    CourseService,
    CourseScoreCalculationService
} from 'app/entities/course';
import { Exercise, ExerciseType } from 'app/entities/exercise';

import { Result } from 'app/entities/result';

const QUIZ_EXERCISE_COLOR = '#17a2b8';
const PROGRAMMING_EXERCISE_COLOR = '#fd7e14';
const MODELING_EXERCISE_COLOR = '#6610f2';
const TEXT_EXERCISE_COLOR = '#B00B6B';
const FILE_UPLOAD_EXERCISE_COLOR = '#2D9C88';

export interface CourseStatisticsDataSet {
    data: Array<number>;
    backgroundColor: Array<any>;
}

@Component({
    selector: 'jhi-course-statistics',
    templateUrl: './course-statistics.component.html',
    styleUrls: ['../course-overview.scss']
})

export class CourseStatisticsComponent implements OnInit, OnDestroy {
    private courseId: number;
    private courseExercises: Exercise[];
    private subscription: Subscription;
    course: Course;

    // absolute score
    totalScore = 0;
    absoluteScores = {};

    // relative score
    totalRelativeScore = 0;
    relativeScores = {};

    // max score
    totalMaxScore = 0;
    totalMaxScores = {};

    groupedExercises: Exercise[][] = [];
    doughnutChartColors = [QUIZ_EXERCISE_COLOR, PROGRAMMING_EXERCISE_COLOR, MODELING_EXERCISE_COLOR, TEXT_EXERCISE_COLOR, FILE_UPLOAD_EXERCISE_COLOR, 'rgba(0, 0, 0, 0.5)'];

    public doughnutChartLabels: string[] = ['Quiz Points', 'Programming Points', 'Modeling Points', 'Text Points', 'File Upload Points', 'Missing Points'];
    public exerciseTitles: object = {
        'quiz': {
            'name': this.translateService.instant('arTeMiSApp.course.quizExercises'),
            'color': QUIZ_EXERCISE_COLOR,
        },
        'modeling': {
            'name': this.translateService.instant('arTeMiSApp.course.modelingExercises'),
            'color': MODELING_EXERCISE_COLOR,
        },
        'programming': {
            'name': this.translateService.instant('arTeMiSApp.course.programmingExercises'),
            'color': PROGRAMMING_EXERCISE_COLOR,
        },
        'text': {
            'name': this.translateService.instant('arTeMiSApp.course.textExercises'),
            'color': TEXT_EXERCISE_COLOR,
        },
        'file-upload': {
            'name': this.translateService.instant('arTeMiSApp.course.fileUploadExercises'),
            'color': FILE_UPLOAD_EXERCISE_COLOR,
        }
    };

    public doughnutChartData: CourseStatisticsDataSet[] = [{
        data: [0, 0, 0, 0, 0, 0],
        backgroundColor: this.doughnutChartColors
    }];

    chartColors = [
        {
            // green
            backgroundColor: 'rgba(40, 167, 69, 0.8)',
            hoverBackgroundColor: 'rgba(40, 167, 69, 1)',
            borderColor: 'rgba(40, 167, 69, 1)',
            pointBackgroundColor: 'rgba(40, 167, 69, 1)',
            pointBorderColor: '#fff',
            pointHoverBackgroundColor: '#fff',
            pointHoverBorderColor: 'rgba(40, 167, 69, 1)'
        }, {
            // red
            backgroundColor: 'rgba(220, 53, 69, 0.8)',
            hoverBackgroundColor: 'rgba(220, 53, 69, 1)',
            borderColor: 'rgba(220, 53, 69, 1)',
            pointBackgroundColor: 'rgba(220, 53, 69, 1)',
            pointBorderColor: '#fff',
            pointHoverBackgroundColor: '#fff',
            pointHoverBorderColor: 'rgba(220, 53, 69, 1)'
        },
    ];
    public barChartOptions: any = {
        scaleShowVerticalLines: false,
        maintainAspectRatio: false,
        responsive: true,
        scales: {
            xAxes: [{
                stacked: true,
                ticks: {
                    autoSkip: false,
                    maxRotation: 0,
                    minRotation: 0
                },
                gridLines: {
                    display: false
                }
            }],
            yAxes: [{
                stacked: true
            }]
        },
        tooltips: {
            backgroundColor: 'rgba(0, 0, 0, 1)',
            callbacks: {
                label: (tooltipItem: any, data: any) => {
                    return data.datasets[tooltipItem.datasetIndex].tooltips[tooltipItem.index];
                }
            }
        }
    };
    public barChartType = 'horizontalBar';

    public doughnutChartType = 'doughnut';
    public totalScoreOptions: object = {
        cutoutPercentage: 75,
        scaleShowVerticalLines: false,
        responsive: false,
        tooltips: {
            backgroundColor: 'rgba(0, 0, 0, 1)',
        }
    };

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private translateService: TranslateService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.parent.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);

        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                this.courseExercises = this.course.exercises;
                this.calculateMaxScores();
                this.calculateAbsoluteScores();
                this.calculateRelativeScores();
                this.groupExercisesByType();
            });
        } else {
            this.courseExercises = this.course.exercises;
            this.calculateMaxScores();
            this.calculateAbsoluteScores();
            this.calculateRelativeScores();
            this.groupExercisesByType();
        }

        this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.exerciseTitles = {
                'quiz': {
                    'name': this.translateService.instant('arTeMiSApp.course.quizExercises'),
                    'color': QUIZ_EXERCISE_COLOR,
                },
                'modeling': {
                    'name': this.translateService.instant('arTeMiSApp.course.modelingExercises'),
                    'color': MODELING_EXERCISE_COLOR,
                },
                'programming': {
                    'name': this.translateService.instant('arTeMiSApp.course.programmingExercises'),
                    'color': PROGRAMMING_EXERCISE_COLOR,
                },
                'text': {
                    'name': this.translateService.instant('arTeMiSApp.course.textExercises'),
                    'color': TEXT_EXERCISE_COLOR,
                },
                'file-upload': {
                    'name': this.translateService.instant('arTeMiSApp.course.fileUploadExercises'),
                    'color': FILE_UPLOAD_EXERCISE_COLOR,
                }
            };
        });
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    groupExercisesByType() {
        const exercises = this.course.exercises;
        const groupedExercises: any[] = [];
        const exerciseTypes: string[] = [];
        exercises.forEach(exercise => {
            let index = exerciseTypes.indexOf(exercise.type);
            if (index === -1) {
                index = exerciseTypes.length;
                exerciseTypes.push(exercise.type);
            }
            if (!groupedExercises[index]) {
                groupedExercises[index] = {
                    type: exercise.type,
                    relativeScore: 0,
                    totalMaxScore: 0,
                    absoluteScore: 0,
                    names: [],
                    scores: {data: [], label: 'Score', tooltips: []},
                    missedScores: {data: [], label: 'Missed score', tooltips: []}
                };
            }
            exercise.participations.forEach(participation => {
                const participationResult = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate);
                const participationScore = participationResult.score;
                const missedScore = 100 - participationScore;
                groupedExercises[index].scores.data.push(participationScore);
                groupedExercises[index].missedScores.data.push(missedScore);
                groupedExercises[index].names.push(exercise.title);
                groupedExercises[index].scores.tooltips.push(`Achieved Score: ${this.absoluteResult(participationResult)} points (${participationScore}%)`);
                if (exercise.maxScore) {
                    groupedExercises[index].missedScores.tooltips.push(`Missed Score: ${exercise.maxScore - this.absoluteResult(participationResult)} points (${missedScore}%)`);
                }
            });
            groupedExercises[index].relativeScore = this.relativeScores[exercise.type];
            groupedExercises[index].totalMaxScore = this.totalMaxScores[exercise.type];
            groupedExercises[index].absoluteScore = this.absoluteScores[exercise.type];
            groupedExercises[index].values = [groupedExercises[index].scores, groupedExercises[index].missedScores];
        });
        this.groupedExercises = groupedExercises;
    }

    absoluteResult(result: Result): number {
        if (!result.resultString) {
            return 0;
        }
        if (result.resultString.indexOf('of') === -1) {
            return parseInt(result.resultString.slice(0, result.resultString.indexOf('points')), 10);
        } else {
            return parseInt(result.resultString.slice(0, result.resultString.indexOf('of')), 10);
        }
    }

    calculateAbsoluteScores(): void {
        const quizzesTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ABSOLUTE_SCORE);
        const programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ABSOLUTE_SCORE);
        const modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ABSOLUTE_SCORE);
        const textExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ABSOLUTE_SCORE);
        const fileUploadExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ABSOLUTE_SCORE);
        this.totalScore = this.calculateTotalScoreForTheCourse(ABSOLUTE_SCORE);
        const totalMissedPoints = this.totalMaxScore - this.totalScore;
        const absoluteScores = {};
        absoluteScores[ExerciseType.QUIZ] = quizzesTotalScore;
        absoluteScores[ExerciseType.PROGRAMMING] = programmingExerciseTotalScore;
        absoluteScores[ExerciseType.MODELING] = modelingExerciseTotalScore;
        absoluteScores[ExerciseType.TEXT] = textExerciseTotalScore;
        absoluteScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseTotalScore;
        this.absoluteScores = absoluteScores;
        this.doughnutChartData[0].data = [quizzesTotalScore, programmingExerciseTotalScore, modelingExerciseTotalScore, textExerciseTotalScore, fileUploadExerciseTotalScore, totalMissedPoints];
    }

    calculateMaxScores() {
        const quizzesTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, MAX_SCORE);
        const programmingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, MAX_SCORE);
        const modelingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, MAX_SCORE);
        const textExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, MAX_SCORE);
        const fileUploadExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, MAX_SCORE);
        const totalMaxScores = {};
        totalMaxScores[ExerciseType.QUIZ] = quizzesTotalMaxScore;
        totalMaxScores[ExerciseType.PROGRAMMING] = programmingExerciseTotalMaxScore;
        totalMaxScores[ExerciseType.MODELING] = modelingExerciseTotalMaxScore;
        totalMaxScores[ExerciseType.TEXT] = textExerciseTotalMaxScore;
        totalMaxScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseTotalMaxScore;
        this.totalMaxScores = totalMaxScores;
        this.totalMaxScore = this.calculateTotalScoreForTheCourse('maxScore');
    }

    calculateRelativeScores(): void {
        const quizzesRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, RELATIVE_SCORE);
        const programmingExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, RELATIVE_SCORE);
        const modelingExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, RELATIVE_SCORE);
        const textExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, RELATIVE_SCORE);
        const fileUploadExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, RELATIVE_SCORE);
        const relativeScores = {};
        relativeScores[ExerciseType.QUIZ] = quizzesRelativeScore;
        relativeScores[ExerciseType.PROGRAMMING] = programmingExerciseRelativeScore;
        relativeScores[ExerciseType.MODELING] = modelingExerciseRelativeScore;
        relativeScores[ExerciseType.TEXT] = textExerciseRelativeScore;
        relativeScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseRelativeScore;
        this.relativeScores = relativeScores;
        this.totalRelativeScore = this.calculateTotalScoreForTheCourse(RELATIVE_SCORE);
    }

    calculateScores(filterFunction: (courseExercise: Exercise) => boolean) {
        let courseExercises = this.courseExercises;
        if (filterFunction) {
            courseExercises = courseExercises.filter(filterFunction);
        }
        return this.courseCalculationService.calculateTotalScores(courseExercises);
    }

    calculateScoreTypeForExerciseType(exerciseType: ExerciseType, scoreType: string): number {
        if (exerciseType !== undefined && scoreType !== undefined) {
            const filterFunction = (courseExercise: Exercise) => courseExercise.type === exerciseType;
            const scores = this.calculateScores(filterFunction);
            return scores.get(scoreType);
        } else {
            return NaN;
        }
    }

    calculateTotalScoreForTheCourse(scoreType: string): number {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
        return scores.get(scoreType);
    }
}
