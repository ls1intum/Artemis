import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { sortBy } from 'lodash';

import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { Exercise, ExerciseType } from 'app/entities/exercise';

import { Result } from 'app/entities/result';
import * as moment from 'moment';
import { InitializationState } from 'app/entities/participation';
import { ABSOLUTE_SCORE, CourseScoreCalculationService, MAX_SCORE, PRESENTATION_SCORE, RELATIVE_SCORE } from 'app/overview';
import { SubmissionExerciseType } from 'app/entities/submission';
import { ProgrammingSubmission } from 'app/entities/programming-submission';

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
    styleUrls: ['../course-overview.scss'],
})
export class CourseStatisticsComponent implements OnInit, OnDestroy {
    readonly QUIZ = ExerciseType.QUIZ;

    private courseId: number;
    private courseExercises: Exercise[];
    private paramSubscription: Subscription;
    private translationSubscription: Subscription;
    course: Course | null;

    // absolute score
    totalScore = 0;
    absoluteScores = {};

    // relative score
    totalRelativeScore = 0;
    relativeScores = {};

    // max score
    totalMaxScore = 0;
    totalMaxScores = {};

    // presentation score
    totalPresentationScore = 0;
    presentationScores = {};
    presentationScoreEnabled = false;

    // this is not an actual exercise, it contains more entries
    groupedExercises: any[][] = [];
    doughnutChartColors = [QUIZ_EXERCISE_COLOR, PROGRAMMING_EXERCISE_COLOR, MODELING_EXERCISE_COLOR, TEXT_EXERCISE_COLOR, FILE_UPLOAD_EXERCISE_COLOR, 'rgba(0, 0, 0, 0.5)'];

    public doughnutChartLabels: string[] = ['Quiz Points', 'Programming Points', 'Modeling Points', 'Text Points', 'File Upload Points', 'Missing Points'];
    public exerciseTitles: object = {
        quiz: {
            name: this.translateService.instant('artemisApp.course.quizExercises'),
            color: QUIZ_EXERCISE_COLOR,
        },
        modeling: {
            name: this.translateService.instant('artemisApp.course.modelingExercises'),
            color: MODELING_EXERCISE_COLOR,
        },
        programming: {
            name: this.translateService.instant('artemisApp.course.programmingExercises'),
            color: PROGRAMMING_EXERCISE_COLOR,
        },
        text: {
            name: this.translateService.instant('artemisApp.course.textExercises'),
            color: TEXT_EXERCISE_COLOR,
        },
        'file-upload': {
            name: this.translateService.instant('artemisApp.course.fileUploadExercises'),
            color: FILE_UPLOAD_EXERCISE_COLOR,
        },
    };

    public doughnutChartData: CourseStatisticsDataSet[] = [
        {
            data: [0, 0, 0, 0, 0, 0],
            backgroundColor: this.doughnutChartColors,
        },
    ];

    chartColors = [
        {
            // green
            backgroundColor: 'rgba(40, 167, 69, 0.8)',
            hoverBackgroundColor: 'rgba(40, 167, 69, 1)',
            borderColor: 'rgba(40, 167, 69, 1)',
            pointBackgroundColor: 'rgba(40, 167, 69, 1)',
            pointBorderColor: '#fff',
            pointHoverBackgroundColor: '#fff',
            pointHoverBorderColor: 'rgba(40, 167, 69, 1)',
        },
        {
            // red
            backgroundColor: 'rgba(220, 53, 69, 0.8)',
            hoverBackgroundColor: 'rgba(220, 53, 69, 1)',
            borderColor: 'rgba(220, 53, 69, 1)',
            pointBackgroundColor: 'rgba(220, 53, 69, 1)',
            pointBorderColor: '#fff',
            pointHoverBackgroundColor: '#fff',
            pointHoverBorderColor: 'rgba(220, 53, 69, 1)',
        },
        {
            // blue
            backgroundColor: 'rgba(62, 138, 204, 0.8)',
            hoverBackgroundColor: 'rgba(62, 138, 204, 1)',
            borderColor: 'rgba(62, 138, 204, 1)',
            pointBackgroundColor: 'rgba(62, 138, 204, 1)',
            pointBorderColor: '#fff',
            pointHoverBackgroundColor: '#fff',
            pointHoverBorderColor: 'rgba(62, 138, 204, 1)',
        },
    ];
    public barChartOptions: any = {
        scaleShowVerticalLines: false,
        maintainAspectRatio: false,
        responsive: true,
        scales: {
            xAxes: [
                {
                    stacked: true,
                    ticks: {
                        autoSkip: false,
                        maxRotation: 0,
                        minRotation: 0,
                    },
                    gridLines: {
                        display: false,
                    },
                },
            ],
            yAxes: [
                {
                    stacked: true,
                },
            ],
        },
        tooltips: {
            backgroundColor: 'rgba(0, 0, 0, 1)',
            width: 120,
            callbacks: {
                label: (tooltipItem: any, data: any) => {
                    return data.datasets[tooltipItem.datasetIndex].tooltips[tooltipItem.index];
                },
                afterLabel: (tooltipItem: any, data: any) => {
                    return data.datasets[tooltipItem.datasetIndex].footer[tooltipItem.index];
                },
            },
        },
    };
    public barChartType = 'horizontalBar';

    public doughnutChartType = 'doughnut';
    public totalScoreOptions: object = {
        cutoutPercentage: 75,
        scaleShowVerticalLines: false,
        responsive: false,
        tooltips: {
            backgroundColor: 'rgba(0, 0, 0, 1)',
        },
    };

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private translateService: TranslateService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.paramSubscription = this.route.parent!.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);

        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                this.courseExercises = this.course!.exercises;
                this.calculateMaxScores();
                this.calculateAbsoluteScores();
                this.calculateRelativeScores();
                this.calculatePresentationScores();
                this.groupExercisesByType();
            });
        } else {
            this.courseExercises = this.course!.exercises;
            this.calculateMaxScores();
            this.calculateAbsoluteScores();
            this.calculateRelativeScores();
            this.calculatePresentationScores();
            this.groupExercisesByType();
        }

        this.translationSubscription = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.exerciseTitles = {
                quiz: {
                    name: this.translateService.instant('artemisApp.course.quizExercises'),
                    color: QUIZ_EXERCISE_COLOR,
                },
                modeling: {
                    name: this.translateService.instant('artemisApp.course.modelingExercises'),
                    color: MODELING_EXERCISE_COLOR,
                },
                programming: {
                    name: this.translateService.instant('artemisApp.course.programmingExercises'),
                    color: PROGRAMMING_EXERCISE_COLOR,
                },
                text: {
                    name: this.translateService.instant('artemisApp.course.textExercises'),
                    color: TEXT_EXERCISE_COLOR,
                },
                'file-upload': {
                    name: this.translateService.instant('artemisApp.course.fileUploadExercises'),
                    color: FILE_UPLOAD_EXERCISE_COLOR,
                },
            };
            this.groupExercisesByType();
        });
    }

    ngOnDestroy() {
        if (this.paramSubscription) {
            this.paramSubscription.unsubscribe();
        }
        if (this.translationSubscription) {
            this.translationSubscription.unsubscribe();
        }
    }

    groupExercisesByType() {
        let exercises = this.course!.exercises;
        const groupedExercises: any[] = [];
        const exerciseTypes: string[] = [];
        // adding several years to be sure that exercises without due date are sorted at the end. this is necessary for the order inside the statistic charts
        exercises = sortBy(exercises, [(exercise: Exercise) => (exercise.dueDate || moment().add(5, 'year')).valueOf()]);
        exercises.forEach(exercise => {
            if (!exercise.dueDate || exercise.dueDate.isBefore(moment()) || exercise.type === ExerciseType.PROGRAMMING) {
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
                        presentationScore: 0,
                        presentationScoreEnabled: exercise.presentationScoreEnabled,
                        names: [],
                        scores: { data: [], label: 'Score', tooltips: [], footer: [] },
                        missedScores: { data: [], label: 'Missed score', tooltips: [], footer: [] },
                        notGraded: { data: [], label: 'Not graded', tooltips: [], footer: [] },
                    };
                }

                exercise.studentParticipations.forEach(participation => {
                    if (participation.results && participation.results.length > 0) {
                        const participationResult = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate!);
                        if (participationResult && participationResult.rated) {
                            const participationScore = participationResult.score;
                            const missedScore = 100 - participationScore;
                            groupedExercises[index].scores.data.push(participationScore);
                            groupedExercises[index].missedScores.data.push(missedScore);
                            groupedExercises[index].notGraded.data.push(0);
                            groupedExercises[index].notGraded.tooltips.push(null);
                            groupedExercises[index].names.push(exercise.title);
                            groupedExercises[index].scores.footer.push(null);
                            groupedExercises[index].missedScores.footer.push(null);
                            groupedExercises[index].notGraded.footer.push(null);
                            if (this.absoluteResult(participationResult) !== null) {
                                groupedExercises[index].scores.tooltips.push(
                                    this.translateService.instant('artemisApp.courseOverview.statistics.exerciseAchievedScore', {
                                        points: this.absoluteResult(participationResult),
                                        percentage: participationScore,
                                    }),
                                );
                                if (exercise.maxScore) {
                                    groupedExercises[index].missedScores.tooltips.push(
                                        this.translateService.instant('artemisApp.courseOverview.statistics.exerciseMissedScore', {
                                            points: exercise.maxScore - this.absoluteResult(participationResult)!,
                                            percentage: missedScore,
                                        }),
                                    );
                                }
                            } else {
                                if (participationScore > 50) {
                                    groupedExercises[index].scores.tooltips.push(`${participationResult.resultString} (${participationScore}%)`);
                                } else {
                                    groupedExercises[index].missedScores.tooltips.push(`${participationResult.resultString} (${participationScore}%)`);
                                }
                            }
                        }
                    } else {
                        if (
                            participation.initializationState === InitializationState.FINISHED &&
                            (!exercise.dueDate || participation.initializationDate!.isBefore(exercise.dueDate!))
                        ) {
                            groupedExercises[index] = this.createPlaceholderChartElement(groupedExercises[index], exercise.title, 'exerciseNotGraded', true);
                        } else {
                            groupedExercises[index] = this.createPlaceholderChartElement(groupedExercises[index], exercise.title, 'exerciseParticipatedAfterDueDate', false);
                        }
                    }
                });
                if (!exercise.studentParticipations || exercise.studentParticipations.length === 0) {
                    groupedExercises[index] = this.createPlaceholderChartElement(groupedExercises[index], exercise.title, 'exerciseNotParticipated', false);
                }
                groupedExercises[index].relativeScore = this.relativeScores[exercise.type];
                groupedExercises[index].totalMaxScore = this.totalMaxScores[exercise.type];
                groupedExercises[index].absoluteScore = this.absoluteScores[exercise.type];
                groupedExercises[index].presentationScore = this.presentationScores[exercise.type];
                // check if presentation score is enabled for at least one exercise
                groupedExercises[index].presentationScoreEnabled = groupedExercises[index].presentationScoreEnabled || exercise.presentationScoreEnabled;
                groupedExercises[index].values = [groupedExercises[index].scores, groupedExercises[index].missedScores, groupedExercises[index].notGraded];
            }
        });
        this.groupedExercises = groupedExercises;
    }

    createPlaceholderChartElement(chartElement: any, exerciseTitle: string, tooltipMessage: string, isNotGraded: boolean) {
        const tooltip = this.translateService.instant(`artemisApp.courseOverview.statistics.${tooltipMessage}`, { exercise: exerciseTitle });
        chartElement.notGraded.data.push(isNotGraded ? 100 : 0);
        chartElement.scores.data.push(0);
        chartElement.missedScores.data.push(isNotGraded ? 0 : 100);
        chartElement.names.push(exerciseTitle);
        chartElement.notGraded.tooltips.push(isNotGraded ? tooltip : null);
        chartElement.scores.tooltips.push(null);
        chartElement.missedScores.tooltips.push(isNotGraded ? null : tooltip);
        chartElement.scores.footer.push(null);
        chartElement.missedScores.footer.push(
            tooltipMessage === 'exerciseParticipatedAfterDueDate' ? this.translateService.instant(`artemisApp.courseOverview.statistics.noPointsForExercise`) : null,
        );
        chartElement.notGraded.footer.push(null);
        return chartElement;
    }

    // TODO: document the implementation of this method --> it is not really obvious
    // TODO: save the return value of this method in the result object (as temp variable) to avoid that this method is invoked all the time
    absoluteResult(result: Result): number | null {
        if (!result.resultString) {
            return 0;
        }
        if (result.resultString && result.resultString.indexOf('failed') !== -1) {
            return null;
        }
        if (result.resultString && result.resultString.indexOf('passed') !== -1) {
            return null;
        }
        if (result.submission && result.submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING && (result.submission as ProgrammingSubmission).buildFailed) {
            return null;
        }
        if (result.resultString.indexOf('of') === -1) {
            if (result.resultString.indexOf('points') === -1) {
                return 0;
            }
            return parseInt(result.resultString.slice(0, result.resultString.indexOf('points')), 10);
        }
        return parseInt(result.resultString.slice(0, result.resultString.indexOf('of')), 10);
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
        this.doughnutChartData[0].data = [
            quizzesTotalScore,
            programmingExerciseTotalScore,
            modelingExerciseTotalScore,
            textExerciseTotalScore,
            fileUploadExerciseTotalScore,
            totalMissedPoints,
        ];
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

    calculatePresentationScores(): void {
        const programmingExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, PRESENTATION_SCORE);
        const modelingExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, PRESENTATION_SCORE);
        const textExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, PRESENTATION_SCORE);
        const fileUploadExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, PRESENTATION_SCORE);
        const presentationScores = {};
        presentationScores[ExerciseType.QUIZ] = 0;
        presentationScores[ExerciseType.PROGRAMMING] = programmingExercisePresentationScore;
        presentationScores[ExerciseType.MODELING] = modelingExercisePresentationScore;
        presentationScores[ExerciseType.TEXT] = textExercisePresentationScore;
        presentationScores[ExerciseType.FILE_UPLOAD] = fileUploadExercisePresentationScore;
        this.presentationScores = presentationScores;
        this.totalPresentationScore = this.calculateTotalScoreForTheCourse(PRESENTATION_SCORE);
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
            return scores.get(scoreType)!;
        } else {
            return NaN;
        }
    }

    calculateTotalScoreForTheCourse(scoreType: string): number {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
        return scores.get(scoreType)!;
    }
}
