import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';

import { CourseService } from 'app/entities/course';
import { CourseScoreCalculationService } from 'app/entities/course';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { Course } from 'app/entities/course/course.model';

const QUIZ_EXERCISE_COLOR = '#17a2b8';
const PROGRAMMING_EXERCISE_COLOR = '#fd7e14';
const MODELING_EXERCISE_COLOR = '#6610f2';

export interface DataSet {
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
    doughnutChartColors = [QUIZ_EXERCISE_COLOR, PROGRAMMING_EXERCISE_COLOR, MODELING_EXERCISE_COLOR, 'rgba(0, 0, 0, 0.5)'];

    public doughnutChartLabels: string[] = ['Quiz Points', 'Programming Points', 'Modeling Points', 'Missing Points'];
    public exerciseTitles: object = {
        'quiz': {
            'name': 'Quiz Exercises',
            'color': QUIZ_EXERCISE_COLOR,
        },
        'modeling': {
            'name': 'Modeling Exercises',
            'color': MODELING_EXERCISE_COLOR,
        },
        'programming': {
            'name': 'Programming Exercises',
            'color': PROGRAMMING_EXERCISE_COLOR,
        },
        'text': {
            'name': 'Text Exercises',
            'color': PROGRAMMING_EXERCISE_COLOR,
        },
        'file-upload': {
            'name': 'File Upload Exercises',
            'color': PROGRAMMING_EXERCISE_COLOR,
        }
    };

    public doughnutChartData: DataSet[] = [{
        data: [0, 0, 0, 0],
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
                const participationScore = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate).score;
                const missedScore = 100 - participationScore;
                groupedExercises[index].scores.data.push(participationScore);
                groupedExercises[index].missedScores.data.push(missedScore);
                groupedExercises[index].names.push(exercise.title);
                groupedExercises[index].scores.tooltips.push(`Achieved Score: ${exercise.maxScore * participationScore / 100} points (${participationScore}%)`);
                groupedExercises[index].missedScores.tooltips.push(`Missed Score: ${exercise.maxScore * missedScore / 100} points (${missedScore}%)`);
            });
            groupedExercises[index].relativeScore = this.relativeScores[exercise.type];
            groupedExercises[index].totalMaxScore = this.totalMaxScores[exercise.type];
            groupedExercises[index].absoluteScore = this.absoluteScores[exercise.type];
            groupedExercises[index].values = [groupedExercises[index].scores, groupedExercises[index].missedScores];
        });
        this.groupedExercises = groupedExercises;
    }

    calculateAbsoluteScores(): void {
        const quizzesTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, 'absoluteScore');
        const programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, 'absoluteScore');
        const modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, 'absoluteScore');
        this.totalScore = this.calculateTotalScoreForTheCourse('absoluteScore');
        const totalMissedPoints = this.totalMaxScore - this.totalScore;
        const absoluteScores = {};
        absoluteScores[ExerciseType.QUIZ] = quizzesTotalScore;
        absoluteScores[ExerciseType.PROGRAMMING] = programmingExerciseTotalScore;
        absoluteScores[ExerciseType.MODELING] = modelingExerciseTotalScore;
        this.absoluteScores = absoluteScores;
        this.doughnutChartData[0].data = [quizzesTotalScore, programmingExerciseTotalScore, modelingExerciseTotalScore, totalMissedPoints];
    }

    calculateMaxScores() {
        const quizzesTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, 'maxScore');
        const programmingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, 'maxScore');
        const modelingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, 'maxScore');
        const totalMaxScores = {};
        totalMaxScores[ExerciseType.QUIZ] = quizzesTotalMaxScore;
        totalMaxScores[ExerciseType.PROGRAMMING] = programmingExerciseTotalMaxScore;
        totalMaxScores[ExerciseType.MODELING] = modelingExerciseTotalMaxScore;
        this.totalMaxScores = totalMaxScores;
        this.totalMaxScore = this.calculateTotalScoreForTheCourse('maxScore');
    }

    calculateRelativeScores(): void {
        const quizzesRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, 'relativeScore');
        const programmingExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, 'relativeScore');
        const modelingExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, 'relativeScore');
        const relativeScores = {};
        relativeScores[ExerciseType.QUIZ] = quizzesRelativeScore;
        relativeScores[ExerciseType.PROGRAMMING] = programmingExerciseRelativeScore;
        relativeScores[ExerciseType.MODELING] = modelingExerciseRelativeScore;
        this.relativeScores = relativeScores;
        this.totalRelativeScore = this.calculateTotalScoreForTheCourse('relativeScore');
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
