import { AfterViewInit, Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { sortBy } from 'lodash-es';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import dayjs from 'dayjs';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { CourseScoreCalculationService, ScoreType } from 'app/overview/course-score-calculation.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { GradeType } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeDTO } from 'app/entities/grade-step.model';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { faClipboard, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/overview/course-overview.component';

const QUIZ_EXERCISE_COLOR = '#17a2b8';
const PROGRAMMING_EXERCISE_COLOR = '#fd7e14';
const MODELING_EXERCISE_COLOR = '#6610f2';
const TEXT_EXERCISE_COLOR = '#B00B6B';
const FILE_UPLOAD_EXERCISE_COLOR = '#2D9C88';

type ExerciseTypeMap = {
    [type in ExerciseType]: number;
};

enum ChartBarTitle {
    NO_DUE_DATE = 'No due date',
    INCLUDED = 'Achieved (included)',
    NOT_INCLUDED = 'Achieved (not included)',
    BONUS = 'Achieved bonus',
    NOT_GRADED = 'Not graded',
    MISSED = 'Missed points',
}

@Component({
    selector: 'jhi-course-statistics',
    templateUrl: './course-statistics.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseStatisticsComponent implements OnInit, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    courseId: number;
    private courseExercises: Exercise[];
    private paramSubscription?: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    course?: Course;

    private courseExercisesNotIncludedInScore: Exercise[];
    currentlyHidingNotIncludedInScoreExercises = true;
    filteredExerciseIDs: number[];

    // TODO: improve the types here and use maps instead of java script objects, also avoid the use of 'any'

    // overall points
    overallPoints = 0;
    overallPointsPerExercise: ExerciseTypeMap;

    // relative score
    totalRelativeScore = 0;
    relativeScoresPerExercise: ExerciseTypeMap;

    // max points
    overallMaxPoints = 0;
    overallMaxPointsPerExercise: ExerciseTypeMap;

    // reachable points
    reachablePoints = 0;
    reachablePointsPerExercise: ExerciseTypeMap;

    // current relative score
    currentRelativeScore = 0;
    currentRelativeScoresPerExercise: ExerciseTypeMap;

    // presentation score
    overallPresentationScore = 0;
    presentationScoresPerExercise: ExerciseTypeMap;

    doughnutChartColors: string[] = [PROGRAMMING_EXERCISE_COLOR, QUIZ_EXERCISE_COLOR, MODELING_EXERCISE_COLOR, TEXT_EXERCISE_COLOR, FILE_UPLOAD_EXERCISE_COLOR, 'red'];

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

    // ngx-charts
    ngxDoughnutData: any[] = [];

    // Labels for the different parts in Your overall points chart
    programmingPointLabel = 'programmingPointLabel';
    quizPointLabel = 'quizPointLabel';
    modelingPointLabel = 'modelingPointLabel';
    textPointLabel = 'textPointLabel';
    fileUploadPointLabel = 'fileUploadPointLabel';
    missingPointsLabel = 'missingPointsLabel';

    ngxDoughnutColor = {
        name: 'Your overall points color',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: this.doughnutChartColors, // colors: orange, turquoise, violet, bordeaux, green, red
    } as Color;

    // arrays representing each exercise group
    ngxModelingExercises: any[] = [];
    ngxProgrammingExercises: any[] = [];
    ngxQuizExercises: any[] = [];
    ngxFileUploadExercises: any[] = [];
    ngxTextExercises: any[] = [];

    // flags determining for each exercise group if at least one exercise has presentation score enabled
    quizPresentationScoreEnabled = false;
    programmingPresentationScoreEnabled = false;
    modelingPresentationScoreEnabled = false;
    textPresentationScoreEnabled = false;
    fileUploadPresentationScoreEnabled = false;

    ngxBarColor = {
        name: 'Score per exercise group',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#e5e5e5', '#32cd32', '#e5e5e5', '#ffd700', '#87ceeb', '#fa8072'], // colors: green, grey, yellow, lightblue, red
    } as Color;

    readonly roundScoreSpecifiedByCourseSettings = roundScoreSpecifiedByCourseSettings;
    readonly barChartTitle = ChartBarTitle;

    // array containing every non-empty exercise group
    ngxExerciseGroups: any[] = [];

    gradingScaleExists = false;
    isBonus = false;
    gradeDTO?: GradeDTO;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faClipboard = faClipboard;

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
        useIndentation: false,
    };

    constructor(
        private courseService: CourseManagementService,
        private courseCalculationService: CourseScoreCalculationService,
        private translateService: TranslateService,
        private route: ActivatedRoute,
        private gradingSystemService: GradingSystemService,
    ) {}

    ngOnInit() {
        // Note: due to lazy loading and router outlet, we use parent 2x here
        this.paramSubscription = this.route.parent?.parent?.params.subscribe((params) => {
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
            this.groupExercisesByType(this.courseExercises);
            this.ngxExerciseGroups = [...this.ngxExerciseGroups];
        });

        this.calculateCourseGrade();
    }

    ngAfterViewInit() {
        // Send our controls template to parent so it will be rendered in the top bar
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    ngOnDestroy() {
        this.translateSubscription.unsubscribe();
        this.courseUpdatesSubscription.unsubscribe();
        this.paramSubscription?.unsubscribe();
    }

    private calculateCourseGrade(): void {
        this.gradingSystemService.matchPercentageToGradeStep(this.totalRelativeScore, this.courseId).subscribe((gradeDTO) => {
            if (gradeDTO) {
                this.gradingScaleExists = true;
                this.gradeDTO = gradeDTO;
                this.isBonus = gradeDTO.gradeType === GradeType.BONUS;
            }
        });
    }

    private onCourseLoad(): void {
        if (this.course?.exercises) {
            this.courseExercises = this.course.exercises;
            this.calculateAndFilterNotIncludedInScore();
            this.calculateMaxPoints();
            this.calculateReachablePoints();
            this.calculateAbsoluteScores();
            this.calculateRelativeScores();
            this.calculatePresentationScores();
            this.calculateCurrentRelativeScores();
            this.groupExercisesByType(this.courseExercises);
        }
    }

    /**
     * Sorts exercises into their corresponding exercise groups and creates dedicated objects that
     * can be processed by ngx-charts in order to visualize the students score for each exercise
     * @param exercises the exercises that should be grouped
     * @private
     */
    private groupExercisesByType(exercises: Exercise[]): void {
        const exerciseTypes: string[] = [];
        this.ngxExerciseGroups = [];
        // this reset is now necessary because of the filtering option that triggers the grouping again.
        this.ngxModelingExercises = [];
        this.ngxProgrammingExercises = [];
        this.ngxQuizExercises = [];
        this.ngxFileUploadExercises = [];
        this.ngxTextExercises = [];

        this.quizPresentationScoreEnabled = false;
        this.programmingPresentationScoreEnabled = false;
        this.modelingPresentationScoreEnabled = false;
        this.textPresentationScoreEnabled = false;
        this.fileUploadPresentationScoreEnabled = false;
        // adding several years to be sure that exercises without due date are sorted at the end. this is necessary for the order inside the statistic charts
        exercises = sortBy(exercises, [(exercise: Exercise) => (exercise.dueDate || dayjs().add(5, 'year')).valueOf()]);
        exercises.forEach((exercise) => {
            if (!exercise.dueDate || exercise.dueDate.isBefore(dayjs()) || exercise.type === ExerciseType.PROGRAMMING) {
                const index = exerciseTypes.indexOf(exercise.type!);
                if (index === -1) {
                    exerciseTypes.push(exercise.type!);
                }
                const series = CourseStatisticsComponent.generateDefaultSeries();

                if (!exercise.studentParticipations || exercise.studentParticipations.length === 0) {
                    series[5].value = 100;
                    series[5].afterDueDate = false;
                    series[5].notParticipated = true;
                    series[5].exerciseTitle = exercise.title;
                    this.pushToData(exercise, series);
                } else {
                    exercise.studentParticipations.forEach((participation) => {
                        if (participation.results && participation.results.length > 0) {
                            const participationResult = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate!);
                            if (participationResult && participationResult.rated) {
                                const roundedParticipationScore = roundScoreSpecifiedByCourseSettings(participationResult.score!, this.course);
                                const cappedParticipationScore = Math.min(roundedParticipationScore, 100);
                                const missedScore = 100 - cappedParticipationScore;
                                const replaced = participationResult.resultString!.replace(',', '.');
                                const split = replaced.split(' ');
                                const missedPoints = Math.min(parseFloat(split[2]) - parseFloat(split[0]), 0);
                                series[5].value = missedScore;
                                series[5].absoluteValue = missedPoints;
                                series[5].afterDueDate = false;
                                series[5].notParticipated = false;

                                this.identifyBar(exercise, series, roundedParticipationScore, parseFloat(split[0]));
                                this.pushToData(exercise, series);
                            }
                        } else {
                            if (
                                participation.initializationState === InitializationState.FINISHED &&
                                (!exercise.dueDate || participation.initializationDate!.isBefore(exercise.dueDate!))
                            ) {
                                series[4].value = 100;
                                series[4].exerciseTitle = exercise.title;
                                this.pushToData(exercise, series);
                            } else {
                                series[5].value = 100;
                                series[5].afterDueDate = true;
                                series[5].exerciseTitle = exercise.title;
                                this.pushToData(exercise, series);
                            }
                        }
                    });
                }
            }
        });
        const allGroups = [this.ngxProgrammingExercises, this.ngxQuizExercises, this.ngxModelingExercises, this.ngxTextExercises, this.ngxFileUploadExercises];
        const allTypes = [ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
        this.pushExerciseGroupsToData(allGroups, allTypes);
    }

    toggleNotIncludedInScoreExercises() {
        if (this.currentlyHidingNotIncludedInScoreExercises) {
            this.courseExercises = this.courseExercises.concat(this.courseExercisesNotIncludedInScore);
            this.filteredExerciseIDs = [];
        } else {
            this.courseExercises = this.courseExercises.filter((exercise) => !this.courseExercisesNotIncludedInScore.includes(exercise));
            this.filteredExerciseIDs = this.courseExercisesNotIncludedInScore.map((exercise) => exercise.id!);
        }
        this.currentlyHidingNotIncludedInScoreExercises = !this.currentlyHidingNotIncludedInScoreExercises;

        this.groupExercisesByType(this.courseExercises);
    }

    /**
     * Generates array containing default configuration for every possible part in one stacked bar
     * @private
     * @returns dedicated object that is requested by ngx-charts in order to visualize one bar in the horizontal bar chart
     */
    private static generateDefaultSeries(): any[] {
        return [
            { name: ChartBarTitle.NO_DUE_DATE, value: 0, absoluteValue: 0 },
            { name: ChartBarTitle.INCLUDED, value: 0, absoluteValue: 0 },
            { name: ChartBarTitle.NOT_INCLUDED, value: 0, absoluteValue: 0 },
            { name: ChartBarTitle.BONUS, value: 0, absoluteValue: 0 },
            { name: ChartBarTitle.NOT_GRADED, value: 0, exerciseTitle: '' },
            { name: ChartBarTitle.MISSED, value: 0, absoluteValue: 0, afterDueDate: false, notParticipated: false, exerciseTitle: '' },
        ];
    }

    /**
     * Calculates absolute score for each exercise group in the course and adds it to the doughnut chart
     * @private
     */
    private calculateAbsoluteScores(): void {
        const quizzesTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ScoreType.ABSOLUTE_SCORE);
        const programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ScoreType.ABSOLUTE_SCORE);
        const modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ScoreType.ABSOLUTE_SCORE);
        const textExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ScoreType.ABSOLUTE_SCORE);
        const fileUploadExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ScoreType.ABSOLUTE_SCORE);
        this.overallPoints = this.calculateTotalScoreForTheCourse(ScoreType.ABSOLUTE_SCORE);
        let totalMissedPoints = this.reachablePoints - this.overallPoints;
        if (totalMissedPoints < 0) {
            totalMissedPoints = 0;
        }
        const absoluteScores = {} as ExerciseTypeMap;
        absoluteScores[ExerciseType.QUIZ] = quizzesTotalScore;
        absoluteScores[ExerciseType.PROGRAMMING] = programmingExerciseTotalScore;
        absoluteScores[ExerciseType.MODELING] = modelingExerciseTotalScore;
        absoluteScores[ExerciseType.TEXT] = textExerciseTotalScore;
        absoluteScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseTotalScore;
        this.overallPointsPerExercise = absoluteScores;
        this.ngxDoughnutData.push({ name: this.programmingPointLabel, value: programmingExerciseTotalScore });
        this.ngxDoughnutData.push({ name: this.quizPointLabel, value: quizzesTotalScore });
        this.ngxDoughnutData.push({ name: this.modelingPointLabel, value: modelingExerciseTotalScore });
        this.ngxDoughnutData.push({ name: this.textPointLabel, value: textExerciseTotalScore });
        this.ngxDoughnutData.push({ name: this.fileUploadPointLabel, value: fileUploadExerciseTotalScore });
        this.ngxDoughnutData.push({ name: this.missingPointsLabel, value: totalMissedPoints });
        this.ngxDoughnutData = [...this.ngxDoughnutData];
    }

    /**
     * Calculates the maximum of points for the course
     * @private
     */
    private calculateMaxPoints(): void {
        const quizzesTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ScoreType.MAX_POINTS);
        const programmingExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ScoreType.MAX_POINTS);
        const modelingExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ScoreType.MAX_POINTS);
        const textExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ScoreType.MAX_POINTS);
        const fileUploadExerciseTotalMaxPoints = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ScoreType.MAX_POINTS);
        const overallMaxPoints = {} as ExerciseTypeMap;
        overallMaxPoints[ExerciseType.QUIZ] = quizzesTotalMaxPoints;
        overallMaxPoints[ExerciseType.PROGRAMMING] = programmingExerciseTotalMaxPoints;
        overallMaxPoints[ExerciseType.MODELING] = modelingExerciseTotalMaxPoints;
        overallMaxPoints[ExerciseType.TEXT] = textExerciseTotalMaxPoints;
        overallMaxPoints[ExerciseType.FILE_UPLOAD] = fileUploadExerciseTotalMaxPoints;
        this.overallMaxPointsPerExercise = overallMaxPoints;
        this.overallMaxPoints = this.calculateTotalScoreForTheCourse(ScoreType.MAX_POINTS);
    }

    /**
     * Calculates the relative score for each exercise group in the course
     * @private
     */
    private calculateRelativeScores(): void {
        const quizzesRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ScoreType.RELATIVE_SCORE);
        const programmingExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ScoreType.RELATIVE_SCORE);
        const modelingExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ScoreType.RELATIVE_SCORE);
        const textExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ScoreType.RELATIVE_SCORE);
        const fileUploadExerciseRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ScoreType.RELATIVE_SCORE);
        const relativeScores = {} as ExerciseTypeMap;
        relativeScores[ExerciseType.QUIZ] = quizzesRelativeScore;
        relativeScores[ExerciseType.PROGRAMMING] = programmingExerciseRelativeScore;
        relativeScores[ExerciseType.MODELING] = modelingExerciseRelativeScore;
        relativeScores[ExerciseType.TEXT] = textExerciseRelativeScore;
        relativeScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseRelativeScore;
        this.relativeScoresPerExercise = relativeScores;
        this.totalRelativeScore = this.calculateTotalScoreForTheCourse(ScoreType.RELATIVE_SCORE);
    }

    /**
     * Calculates the reachable points for the course
     * @private
     */
    private calculateReachablePoints(): void {
        const quizzesReachablePoints = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ScoreType.REACHABLE_POINTS);
        const programmingExercisesReachablePoints = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ScoreType.REACHABLE_POINTS);
        const modelingExercisesReachablePoints = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ScoreType.REACHABLE_POINTS);
        const textExercisesReachablePoints = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ScoreType.REACHABLE_POINTS);
        const fileUploadExercisesReachablePoints = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ScoreType.REACHABLE_POINTS);
        const reachablePoints = {} as ExerciseTypeMap;
        reachablePoints[ExerciseType.QUIZ] = quizzesReachablePoints;
        reachablePoints[ExerciseType.PROGRAMMING] = programmingExercisesReachablePoints;
        reachablePoints[ExerciseType.MODELING] = modelingExercisesReachablePoints;
        reachablePoints[ExerciseType.TEXT] = textExercisesReachablePoints;
        reachablePoints[ExerciseType.FILE_UPLOAD] = fileUploadExercisesReachablePoints;
        this.reachablePointsPerExercise = reachablePoints;
        this.reachablePoints = this.calculateTotalScoreForTheCourse(ScoreType.REACHABLE_POINTS);
    }

    /**
     * Calculates the current relative score for the course
     * @private
     */
    private calculateCurrentRelativeScores(): void {
        const quizzesCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ScoreType.CURRENT_RELATIVE_SCORE);
        const programmingExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ScoreType.CURRENT_RELATIVE_SCORE);
        const modelingExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ScoreType.CURRENT_RELATIVE_SCORE);
        const textExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ScoreType.CURRENT_RELATIVE_SCORE);
        const fileUploadExerciseCurrentRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ScoreType.CURRENT_RELATIVE_SCORE);
        const currentRelativeScores = {} as ExerciseTypeMap;
        currentRelativeScores[ExerciseType.QUIZ] = quizzesCurrentRelativeScore;
        currentRelativeScores[ExerciseType.PROGRAMMING] = programmingExerciseCurrentRelativeScore;
        currentRelativeScores[ExerciseType.MODELING] = modelingExerciseCurrentRelativeScore;
        currentRelativeScores[ExerciseType.TEXT] = textExerciseCurrentRelativeScore;
        currentRelativeScores[ExerciseType.FILE_UPLOAD] = fileUploadExerciseCurrentRelativeScore;
        this.currentRelativeScoresPerExercise = currentRelativeScores;
        this.currentRelativeScore = this.calculateTotalScoreForTheCourse(ScoreType.CURRENT_RELATIVE_SCORE);
    }

    /**
     * Calculates the presentation score for the course
     * @private
     */
    private calculatePresentationScores(): void {
        const programmingExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ScoreType.PRESENTATION_SCORE);
        const modelingExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ScoreType.PRESENTATION_SCORE);
        const textExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.TEXT, ScoreType.PRESENTATION_SCORE);
        const fileUploadExercisePresentationScore = this.calculateScoreTypeForExerciseType(ExerciseType.FILE_UPLOAD, ScoreType.PRESENTATION_SCORE);
        // TODO: use a proper type here, e.g. a map
        const presentationScores = {} as ExerciseTypeMap;
        presentationScores[ExerciseType.QUIZ] = 0;
        presentationScores[ExerciseType.PROGRAMMING] = programmingExercisePresentationScore;
        presentationScores[ExerciseType.MODELING] = modelingExercisePresentationScore;
        presentationScores[ExerciseType.TEXT] = textExercisePresentationScore;
        presentationScores[ExerciseType.FILE_UPLOAD] = fileUploadExercisePresentationScore;
        this.presentationScoresPerExercise = presentationScores;
        this.overallPresentationScore = this.calculateTotalScoreForTheCourse(ScoreType.PRESENTATION_SCORE);
    }

    /**
     * Calculates the total score for every exercise in the course satisfying the filter function
     * @param filterFunction the filter the exercises have to satisfy
     * @returns map containing score for every score type
     * @private
     */
    private calculateScores(filterFunction: (courseExercise: Exercise) => boolean): Map<string, number> {
        let courseExercises = this.courseExercises;
        if (filterFunction) {
            courseExercises = courseExercises.filter(filterFunction);
        }
        return this.courseCalculationService.calculateTotalScores(courseExercises, this.course!);
    }

    /**
     * Calculates an arbitrary score type for an arbitrary exercise type
     * @param exerciseType the exercise type for which the score should be calculates. Must be an element of {Programming, Modeling, Quiz, Text, File upload}
     * @param scoreType the score type that should be calculated. Element of {Absolute score, Max points,Current relative score,Presentation score,Reachable points,Relative score}
     * @returns requested score value
     * @private
     */
    private calculateScoreTypeForExerciseType(exerciseType: ExerciseType, scoreType: ScoreType): number {
        if (exerciseType != undefined && scoreType != undefined) {
            const filterFunction = (courseExercise: Exercise) => courseExercise.type === exerciseType;
            const scores = this.calculateScores(filterFunction);
            return scores.get(scoreType)!;
        } else {
            return NaN;
        }
    }

    /**
     * Calculates a score type for the whole course
     * @param scoreType the score type that should be calculated. Element of {Absolute score, Max points,Current relative score,Presentation score,Reachable points,Relative score}
     * @returns requested score type value
     * @private
     */
    private calculateTotalScoreForTheCourse(scoreType: ScoreType): number {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises, this.course!);
        return scores.get(scoreType)!;
    }

    calculateAndFilterNotIncludedInScore() {
        this.courseExercisesNotIncludedInScore = this.courseExercises.filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED);
        this.courseExercises = this.courseExercises.filter((exercise) => !this.courseExercisesNotIncludedInScore.includes(exercise));
        this.filteredExerciseIDs = this.courseExercisesNotIncludedInScore.map((exercise) => exercise.id!);
    }

    /**
     * Depending on the type of the exercise, it adds a new object containing
     * the different scores of the correspnding exercise group of the chart
     * @param exercise an arbitrary exercise of a course
     * @param series an array of dedicated objects containing the students performance in this exercise that is visualized by the chart
     * @private
     */
    private pushToData(exercise: Exercise, series: any[]): void {
        switch (exercise.type!) {
            case ExerciseType.MODELING:
                this.ngxModelingExercises.push({
                    name: exercise.title,
                    series,
                });
                this.modelingPresentationScoreEnabled = this.modelingPresentationScoreEnabled || exercise.presentationScoreEnabled!;
                break;
            case ExerciseType.PROGRAMMING:
                series.forEach((part: any) => {
                    part.isProgrammingExercise = true;
                });
                this.ngxProgrammingExercises.push({
                    name: exercise.title,
                    series,
                });
                this.programmingPresentationScoreEnabled = this.programmingPresentationScoreEnabled || exercise.presentationScoreEnabled!;
                break;
            case ExerciseType.QUIZ:
                this.ngxQuizExercises.push({
                    name: exercise.title,
                    series,
                });
                this.quizPresentationScoreEnabled = this.quizPresentationScoreEnabled || exercise.presentationScoreEnabled!;
                break;
            case ExerciseType.FILE_UPLOAD:
                this.ngxFileUploadExercises.push({
                    name: exercise.title,
                    series,
                });
                this.fileUploadPresentationScoreEnabled = this.fileUploadPresentationScoreEnabled || exercise.presentationScoreEnabled!;
                break;
            case ExerciseType.TEXT:
                this.ngxTextExercises.push({
                    name: exercise.title,
                    series,
                });
                this.textPresentationScoreEnabled = this.textPresentationScoreEnabled || exercise.presentationScoreEnabled!;
                break;
        }
    }

    /**
     * Adds some meta data to every non-empty exercise group and pushes it to ngxExerciseGroups
     * @param exerciseGroups array containing the exercise groups
     * @param types array containing all possible exercise types (programming, modeling, quiz, text, file upload)
     * @private
     */
    private pushExerciseGroupsToData(exerciseGroups: any[], types: ExerciseType[]): void {
        exerciseGroups.forEach((exerciseGroup, index) => {
            if (exerciseGroup.length > 0) {
                exerciseGroup[0] = {
                    name: exerciseGroup[0].name,
                    series: exerciseGroup[0].series,
                    type: types[index],
                    absoluteScore: this.overallPointsPerExercise[types[index]],
                    relativeScore: this.relativeScoresPerExercise[types[index]],
                    reachableScore: this.reachablePointsPerExercise[types[index]],
                    currentRelativeScore: this.currentRelativeScoresPerExercise[types[index]],
                    overallMaxPoints: this.overallMaxPointsPerExercise[types[index]],
                    presentationScore: this.presentationScoresPerExercise[types[index]],
                    presentationScoreEnabled: false,
                    barPadding: this.setBarPadding(exerciseGroup.length),
                    xScaleMax: this.setXScaleMax(exerciseGroup),
                };
                switch (types[index]) {
                    case ExerciseType.MODELING:
                        exerciseGroup[0].presentationScoreEnabled = this.modelingPresentationScoreEnabled;
                        break;
                    case ExerciseType.PROGRAMMING:
                        exerciseGroup[0].presentationScoreEnabled = this.programmingPresentationScoreEnabled;
                        break;
                    case ExerciseType.QUIZ:
                        exerciseGroup[0].presentationScoreEnabled = this.quizPresentationScoreEnabled;
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        exerciseGroup[0].presentationScoreEnabled = this.fileUploadPresentationScoreEnabled;
                        break;
                    case ExerciseType.TEXT:
                        exerciseGroup[0].presentationScoreEnabled = this.textPresentationScoreEnabled;
                        break;
                }
                this.ngxExerciseGroups.push(exerciseGroup);
            }
        });
    }

    /**
     * Calculates the bar padding dependent of the amount of exercises in one exercise group
     * ngx-charts only allows setting an absolute value for the bar padding in px, which leads to unpleasant
     * proportions in the bar charts for sufficiently large exercise groups
     * @param groupSize the amount of exercises in a specific group
     * @returns bar padding in px
     */
    setBarPadding(groupSize: number): number {
        return groupSize < 10 ? 8 : groupSize < 15 ? 4 : 2;
    }

    /**
     * Depending on if the exercise has a due date and how its score is included,
     * adds the student score to the corresponding bar.
     * @param exercise the exercise of interest which has to be displayed by the chart
     * @param series the series the students score gets pushed to
     * @param roundedParticipationScore the students relative score
     * @param split the students absolute score
     * @private
     */
    private identifyBar(exercise: Exercise, series: any[], roundedParticipationScore: number, split: number): void {
        if (!exercise.dueDate) {
            series[0].value = roundedParticipationScore;
            series[0].absoluteValue = split;
        } else {
            switch (exercise.includedInOverallScore) {
                case IncludedInOverallScore.INCLUDED_COMPLETELY:
                    series[1].value = roundedParticipationScore;
                    series[1].absoluteValue = split;
                    break;
                case IncludedInOverallScore.NOT_INCLUDED:
                    series[2].value = roundedParticipationScore;
                    series[2].absoluteValue = split;
                    break;
                case IncludedInOverallScore.INCLUDED_AS_BONUS:
                    series[3].value = roundedParticipationScore;
                    series[3].absoluteValue = split;
                    break;
            }
        }
    }

    /**
     * Sets the maximum scale on the x axis if there are exercises with > 100%
     * @param exerciseGroup the exercise group
     * @private
     * @returns maximum value visible on xAxis
     */
    private setXScaleMax(exerciseGroup: any[]): number {
        let xScaleMax = 100;
        exerciseGroup.forEach((exercise: any) => {
            const maxScore = Math.max(exercise.series[0].value, exercise.series[1].value, exercise.series[2].value, exercise.series[3].value);
            xScaleMax = xScaleMax > maxScore ? xScaleMax : Math.ceil(maxScore);
        });
        return xScaleMax;
    }
}
