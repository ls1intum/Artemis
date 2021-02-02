import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { TranslateService } from '@ngx-translate/core';
import { sortBy } from 'lodash';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import {
    ABSOLUTE_SCORE,
    CourseScoreCalculationService,
    CURRENT_RELATIVE_SCORE,
    MAX_POINTS,
    PRESENTATION_SCORE,
    REACHABLE_SCORE,
    RELATIVE_SCORE,
} from 'app/overview/course-score-calculation.service';
import { InitializationState } from 'app/entities/participation/participation.model';

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

    courseId: number;
    private courseExercises: Exercise[];
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    course?: Course;

    // absolute score
    totalScore = 0;
    absoluteScores = {};

    // relative score
    totalRelativeScore = 0;
    relativeScores = {};

    // max points
    overallMaxPoints = 0;
    overallMaxPointsPerExercise = {};

    // current max score
    reachableScore = 0;
    reachableScores = {};

    // current max score
    currentRelativeScore = 0;
    currentRelativeScores = {};

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
        private courseService: CourseManagementService,
        private courseCalculationService: CourseScoreCalculationService,
        private translateService: TranslateService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseService.getCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.courseCalculationService.updateCourse(course);
            this.course = this.courseCalculationService.getCourse(this.courseId);
            this.onCourseLoad();
        });

        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
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
        this.translateSubscription.unsubscribe();
        this.courseUpdatesSubscription.unsubscribe();
        this.paramSubscription.unsubscribe();
    }

    private onCourseLoad() {
        this.courseExercises = this.course!.exercises!;
        this.calculateMaxPoints();
        this.calculateReachableScores();
        this.calculateAbsoluteScores();
        this.calculateRelativeScores();
        this.calculatePresentationScores();
        this.calculateCurrentRelativeScores();
        this.groupExercisesByType();
    }

    groupExercisesByType() {
        let exercises = this.course!.exercises;
        const groupedExercises: any[] = [];
        const exerciseTypes: string[] = [];
        // adding several years to be sure that exercises without due date are sorted at the end. this is necessary for the order inside the statistic charts
        exercises = sortBy(exercises, [(exercise: Exercise) => (exercise.dueDate || moment().add(5, 'year')).valueOf()]);
        exercises.forEach((exercise) => {
            if (!exercise.dueDate || exercise.dueDate.isBefore(moment()) || exercise.type === ExerciseType.PROGRAMMING) {
                let index = exerciseTypes.indexOf(exercise.type!);
                if (index === -1) {
                    index = exerciseTypes.length;
                    exerciseTypes.push(exercise.type!);
                }
                if (!groupedExercises[index]) {
                    groupedExercises[index] = {
                        type: exercise.type,
                        relativeScore: 0,
                        overallMaxPoints: 0,
                        absoluteScore: 0,
                        presentationScore: 0,
                        presentationScoreEnabled: exercise.presentationScoreEnabled,
                        names: [],
                        scores: { data: [], label: 'Score', tooltips: [], footer: [], backgroundColor: [], hoverBackgroundColor: [] }, // part of dataset
                        missedScores: { data: [], label: 'Missed score', tooltips: [], footer: [], backgroundColor: 'Salmon', hoverBackgroundColor: 'Salmon' }, // part of dataset
                        notGraded: { data: [], label: 'Not graded', tooltips: [], footer: [], backgroundColor: 'SkyBlue', hoverBackgroundColor: 'SkyBlue' }, // part of dataset
                        reachableScore: 0,
                        currentRelativeScore: 0,
                    };
                }

                if (!exercise.studentParticipations || exercise.studentParticipations.length === 0) {
                    groupedExercises[index] = this.createPlaceholderChartElement(groupedExercises[index], exercise.title!, 'exerciseNotParticipated', false);
                } else {
                    const scoreColor = this.getScoreColor(exercise.includedInOverallScore!);
                    exercise.studentParticipations.forEach((participation) => {
                        if (participation.results && participation.results.length > 0) {
                            const participationResult = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate!);
                            if (participationResult && participationResult.rated) {
                                const cappedParticipationScore = participationResult.score! >= 100 ? 100 : participationResult.score!;
                                const missedScore = 100 - cappedParticipationScore;
                                groupedExercises[index].scores.data.push(participationResult.score!);
                                groupedExercises[index].scores.backgroundColor.push(scoreColor);
                                groupedExercises[index].scores.hoverBackgroundColor.push(scoreColor);
                                groupedExercises[index].missedScores.data.push(missedScore);
                                groupedExercises[index].notGraded.data.push(0);
                                groupedExercises[index].notGraded.tooltips.push(null);
                                groupedExercises[index].names.push(exercise.title);
                                groupedExercises[index].scores.footer.push(null);
                                groupedExercises[index].missedScores.footer.push(null);
                                groupedExercises[index].notGraded.footer.push(null);
                                this.generateTooltip(participationResult, groupedExercises[index], exercise.includedInOverallScore!);
                            }
                        } else {
                            if (
                                participation.initializationState === InitializationState.FINISHED &&
                                (!exercise.dueDate || participation.initializationDate!.isBefore(exercise.dueDate!))
                            ) {
                                groupedExercises[index] = this.createPlaceholderChartElement(groupedExercises[index], exercise.title!, 'exerciseNotGraded', true);
                            } else {
                                groupedExercises[index] = this.createPlaceholderChartElement(groupedExercises[index], exercise.title!, 'exerciseParticipatedAfterDueDate', false);
                            }
                        }
                    });
                }

                groupedExercises[index].relativeScore = this.relativeScores[exercise.type!];
                groupedExercises[index].overallMaxPoints = this.overallMaxPointsPerExercise[exercise.type!];
                groupedExercises[index].currentRelativeScore = this.currentRelativeScores[exercise.type!];
                groupedExercises[index].reachableScore = this.reachableScores[exercise.type!];
                groupedExercises[index].absoluteScore = this.absoluteScores[exercise.type!];
                groupedExercises[index].presentationScore = this.presentationScores[exercise.type!];
                // check if presentation score is enabled for at least one exercise
                groupedExercises[index].presentationScoreEnabled = groupedExercises[index].presentationScoreEnabled || exercise.presentationScoreEnabled;
                groupedExercises[index].values = [groupedExercises[index].scores, groupedExercises[index].missedScores, groupedExercises[index].notGraded];
            }
        });
        this.groupedExercises = groupedExercises;
    }

    getScoreColor(includedInOverallScore: IncludedInOverallScore): string {
        switch (includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return 'limeGreen';
            case IncludedInOverallScore.NOT_INCLUDED:
                return 'lightGray';
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return 'gold';
        }
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

    generateTooltipExtension(includedInOverallScore: IncludedInOverallScore): string {
        switch (includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return ' | ' + this.translateService.instant('artemisApp.courseOverview.statistics.bonusPointTooltip');
            case IncludedInOverallScore.NOT_INCLUDED:
                return ' | ' + this.translateService.instant('artemisApp.courseOverview.statistics.notIncludedTooltip');
            default:
                return '';
        }
    }

    generateTooltip(result: Result, groupedExercise: any, includedInOverallScore: IncludedInOverallScore): void {
        if (!result.resultString) {
            groupedExercise.scores.tooltips.push(
                this.translateService.instant('artemisApp.courseOverview.statistics.exerciseAchievedScore', {
                    points: 0,
                    percentage: 0,
                }) + this.generateTooltipExtension(includedInOverallScore),
            );
            groupedExercise.missedScores.tooltips.push(
                this.translateService.instant('artemisApp.courseOverview.statistics.exerciseMissedScore', {
                    points: '',
                    percentage: 100,
                }),
            );
            return;
        }

        const replaced = result.resultString.replace(',', '.');
        const split = replaced.split(' ');

        const missedPoints = parseFloat(split[2]) - parseFloat(split[0]) > 0 ? parseFloat(split[2]) - parseFloat(split[0]) : 0;
        // This score is used to cap bonus points, so that we not have negative values for the missedScores
        const score = result.score! >= 100 ? 100 : result.score!;
        // custom result strings
        if (!replaced.includes('passed') && !replaced.includes('points')) {
            if (result.score! >= 50) {
                groupedExercise.scores.tooltips.push(`${result.resultString} (${result.score}%)` + this.generateTooltipExtension(includedInOverallScore));
                groupedExercise.missedScores.tooltips.push(`(${100 - score}%)`);
            } else {
                groupedExercise.scores.tooltips.push(`(${result.score}%)` + this.generateTooltipExtension(includedInOverallScore));
                groupedExercise.missedScores.tooltips.push(`${result.resultString} (${100 - score}%)`);
            }

            return;
        }

        // exercise results strings are mostly 'x points' or 'x of y points'
        if (replaced.includes('points')) {
            if (split.length === 2) {
                groupedExercise.scores.tooltips.push(
                    this.translateService.instant('artemisApp.courseOverview.statistics.exerciseAchievedScore', {
                        points: parseFloat(split[0]),
                        percentage: result.score,
                    }) + this.generateTooltipExtension(includedInOverallScore),
                );
                groupedExercise.missedScores.tooltips.push(
                    this.translateService.instant('artemisApp.courseOverview.statistics.exerciseMissedScore', {
                        points: '',
                        percentage: 100 - score,
                    }),
                );
                return;
            }
            if (split.length === 4) {
                groupedExercise.scores.tooltips.push(
                    this.translateService.instant('artemisApp.courseOverview.statistics.exerciseAchievedScore', {
                        points: parseFloat(split[0]),
                        percentage: result.score,
                    }) + this.generateTooltipExtension(includedInOverallScore),
                );
                groupedExercise.missedScores.tooltips.push(
                    this.translateService.instant('artemisApp.courseOverview.statistics.exerciseMissedScore', {
                        points: missedPoints,
                        percentage: 100 - score,
                    }),
                );
                return;
            }
        }

        // programming exercise result strings are mostly 'x passed' or 'x of y passed'
        if (replaced.includes('passed')) {
            if (split.length === 2) {
                groupedExercise.scores.tooltips.push(parseFloat(split[0]) + ' tests passed (' + result.score + '%).' + this.generateTooltipExtension(includedInOverallScore));
                groupedExercise.missedScores.tooltips.push('(' + (100 - score) + '%)');
                return;
            }
            if (split.length === 4) {
                groupedExercise.scores.tooltips.push(parseFloat(split[0]) + ' tests passed (' + result.score + '%).' + this.generateTooltipExtension(includedInOverallScore));
                groupedExercise.missedScores.tooltips.push(missedPoints + ' tests failed (' + (100 - score) + '%).');
                return;
            }
        }
    }

    calculateAbsoluteScores(): void {
        const quizzesTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ABSOLUTE_SCORE);
        const programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ABSOLUTE_SCORE);
        const modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ABSOLUTE_SCORE);
        const textExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ABSOLUTE_SCORE);
        const fileUploadExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ABSOLUTE_SCORE);
        this.totalScore = this.calculateTotalScoreForTheCourse(ABSOLUTE_SCORE);
        let totalMissedPoints = this.reachableScore - this.totalScore;
        if (totalMissedPoints < 0) {
            totalMissedPoints = 0;
        }
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

    calculateMaxPoints() {
        const quizzesTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, MAX_POINTS);
        const programmingExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, MAX_POINTS);
        const modelingExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, MAX_POINTS);
        const textExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, MAX_POINTS);
        const fileUploadExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, MAX_POINTS);
        const overallMaxPoints = {};
        overallMaxPoints[ExerciseType.QUIZ] = quizzesTotalMaxPoints;
        overallMaxPoints[ExerciseType.PROGRAMMING] = programmingExerciseTotalMaxPoints;
        overallMaxPoints[ExerciseType.MODELING] = modelingExerciseTotalMaxPoints;
        overallMaxPoints[ExerciseType.TEXT] = textExerciseTotalMaxPoints;
        overallMaxPoints[ExerciseType.FILE_UPLOAD] = fileUploadExerciseTotalMaxPoints;
        this.overallMaxPointsPerExercise = overallMaxPoints;
        this.overallMaxPoints = this.calculateTotalScoreForTheCourse('maxScore');
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

    calculateReachableScores() {
        const quizzesTotalCurrentMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, REACHABLE_SCORE);
        const programmingExerciseTotalCurrentMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, REACHABLE_SCORE);
        const modelingExerciseTotalCurrentMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, REACHABLE_SCORE);
        const textExerciseTotalCurrentMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, REACHABLE_SCORE);
        const fileUploadExerciseTotalCurrentMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, REACHABLE_SCORE);
        const reachableScores = {};
        reachableScores[ExerciseType.QUIZ] = quizzesTotalCurrentMaxScore;
        reachableScores[ExerciseType.PROGRAMMING] = programmingExerciseTotalCurrentMaxScore;
        reachableScores[ExerciseType.MODELING] = modelingExerciseTotalCurrentMaxScore;
        reachableScores[ExerciseType.TEXT] = textExerciseTotalCurrentMaxScore;
        reachableScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseTotalCurrentMaxScore;
        this.reachableScores = reachableScores;
        this.reachableScore = this.calculateTotalScoreForTheCourse(REACHABLE_SCORE);
    }

    calculateCurrentRelativeScores(): void {
        const quizzesCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, CURRENT_RELATIVE_SCORE);
        const programmingExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, CURRENT_RELATIVE_SCORE);
        const modelingExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, CURRENT_RELATIVE_SCORE);
        const textExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, CURRENT_RELATIVE_SCORE);
        const fileUploadExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, CURRENT_RELATIVE_SCORE);
        const currentRelativeScores = {};
        currentRelativeScores[ExerciseType.QUIZ] = quizzesCurrentRelativeScore;
        currentRelativeScores[ExerciseType.PROGRAMMING] = programmingExerciseCurrentRelativeScore;
        currentRelativeScores[ExerciseType.MODELING] = modelingExerciseCurrentRelativeScore;
        currentRelativeScores[ExerciseType.TEXT] = textExerciseCurrentRelativeScore;
        currentRelativeScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseCurrentRelativeScore;
        this.currentRelativeScores = currentRelativeScores;
        this.currentRelativeScore = this.calculateTotalScoreForTheCourse(CURRENT_RELATIVE_SCORE);
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
        if (exerciseType != undefined && scoreType != undefined) {
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
