import { AfterViewInit, Component, EventEmitter, OnDestroy, OnInit, TemplateRef, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faClipboard, faFilter, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ChartData, ChartOptions, TooltipItem } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { ParticipationResultDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore, ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradeDTO } from 'app/assessment/shared/entities/grade-step.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared-ui/tab-bar/tab-bar';
import { ChartCategoryFilter } from 'app/exercise/chart/chart-category-filter';
import { ChartMultiSeriesEntry, ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { multiSeriesToStackedBarData, singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions, doughnutChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ScoreType } from 'app/foundation/constants/score-type.constants';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import dayjs from 'dayjs/esm';
import { sortBy } from 'lodash-es';
import { Subject, Subscription } from 'rxjs';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ExerciseScoresChartComponent } from 'app/course/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

const QUIZ_EXERCISE_COLOR = '#17a2b8';
const PROGRAMMING_EXERCISE_COLOR = '#fd7e14';
const MODELING_EXERCISE_COLOR = '#6610f2';
const TEXT_EXERCISE_COLOR = '#B00B6B';
const FILE_UPLOAD_EXERCISE_COLOR = '#2D9C88';

interface YourOverallPointsEntry extends ChartSeriesEntry {
    color: string;
}

export class Series {
    [extra: string]: unknown;
    name: ChartBarTitle;
    value = 0;
    absoluteValue = 0;
    afterDueDate = false;
    notParticipated = false;
    exerciseTitle? = '';
    exerciseId = 0;
    isProgrammingExercise = false;
    constructor(name: ChartBarTitle) {
        this.name = name;
    }
}

export class NgxExercise {
    name?: string;
    series: Series[];
    presentationScoreEnabled = false;
    type: ExerciseType;
    absoluteScore = 0;
    relativeScore = 0;
    reachablePoints = 0;
    currentRelativeScore = 0;
    overallMaxPoints = 0;
    presentationScore = 0;
    xScaleMax = 0;
    height = 0;
    constructor(name: string | undefined, series: Series[], type: ExerciseType) {
        this.name = name;
        this.series = series;
        this.type = type;
    }
}

export class ExerciseTitle {
    name: string;
    color: string;
    constructor(name: string, color: string) {
        this.name = name;
        this.color = color;
    }
}

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
    styleUrls: ['../course-overview/course-overview.scss'],
    imports: [
        NgbDropdown,
        NgbDropdownToggle,
        FaIconComponent,
        TranslateDirective,
        NgbDropdownMenu,
        DocumentationButtonComponent,
        ChartModule,
        NgbTooltip,
        RouterLink,
        ExerciseScoresChartComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
    ],
})
export class CourseStatisticsComponent implements OnInit, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    controlsRendered = new EventEmitter<void>();
    private courseStorageService = inject(CourseStorageService);
    private scoresStorageService = inject(ScoresStorageService);
    private translateService = inject(TranslateService);
    private route = inject(ActivatedRoute);
    private gradingService = inject(GradingService);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    categoryFilter = inject(ChartCategoryFilter);

    readonly documentationType: DocumentationType = 'StudentStatistics';

    courseId: number;
    private courseExercises: Exercise[];
    private paramSubscription?: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    readonly course = signal<Course | undefined>(undefined);
    readonly numberOfAppliedFilters = signal<number>(0);

    private courseExercisesNotIncludedInScore: Exercise[];
    private courseExercisesFilteredByCategories: Exercise[];
    readonly currentlyHidingNotIncludedInScoreExercises = signal<boolean>(false);
    readonly filteredExerciseIDs = signal<number[]>([]);

    // Icons
    faFilter = faFilter;

    // overall points
    readonly overallPoints = signal<number>(0);
    overallPointsPerExercise = new Map<ExerciseType, number>();

    // relative score
    readonly totalRelativeScore = signal<number>(0);
    relativeScoresPerExercise = new Map<ExerciseType, number>();

    // max points
    readonly overallMaxPoints = signal<number>(0);
    overallMaxPointsPerExercise = new Map<ExerciseType, number>();

    // reachable points
    readonly reachablePoints = signal<number>(0);
    reachablePointsPerExercise = new Map<ExerciseType, number>();

    // current relative score
    readonly currentRelativeScore = signal<number>(0);
    currentRelativeScoresPerExercise = new Map<ExerciseType, number>();

    // presentation score
    readonly overallPresentationScore = signal<number>(0);
    presentationScoresPerExercise = new Map<ExerciseType, number>();

    // reachable presentation points
    readonly reachablePresentationPoints = signal<number>(0);

    doughnutChartColors: string[] = [
        PROGRAMMING_EXERCISE_COLOR,
        QUIZ_EXERCISE_COLOR,
        MODELING_EXERCISE_COLOR,
        TEXT_EXERCISE_COLOR,
        FILE_UPLOAD_EXERCISE_COLOR,
        GraphColors.LIGHT_BLUE,
        GraphColors.RED,
    ];

    exerciseTitles = new Map<ExerciseType, ExerciseTitle>();

    // entries of the "Your overall points" doughnut chart (name is a translation key, color the slice color)
    readonly doughnutChartEntries = signal<YourOverallPointsEntry[]>([]);

    // Labels for the different parts in Your overall points chart
    programmingPointLabel = 'programmingPointLabel';
    quizPointLabel = 'quizPointLabel';
    modelingPointLabel = 'modelingPointLabel';
    textPointLabel = 'textPointLabel';
    fileUploadPointLabel = 'fileUploadPointLabel';
    presentationPointsLabel = 'presentationPointsLabel';
    missingPointsLabel = 'missingPointsLabel';
    labels = [
        this.programmingPointLabel,
        this.quizPointLabel,
        this.modelingPointLabel,
        this.textPointLabel,
        this.fileUploadPointLabel,
        this.presentationPointsLabel,
        this.missingPointsLabel,
    ];

    // flags determining for each exercise group if at least one exercise has presentation score enabled
    presentationScoreEnabled = new Map<ExerciseType, boolean>();

    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly CHART_HEIGHT = 25;
    readonly BAR_PADDING = 4;
    readonly DEFAULT_SIZE = 50; // additional space for the x-axis and its labels

    // map containing every non-empty exercise group (one chart per group)
    readonly ngxExerciseGroups = signal(new Map<ExerciseType, NgxExercise[]>());
    // working copy that is populated while grouping exercises before it is published to the signal
    private exerciseGroupsInProgress = new Map<ExerciseType, NgxExercise[]>();

    private readonly chartColorService = inject(ChartColorService);
    // colors of the doughnut chart slices (depend on the displayed entries)
    private readonly doughnutColors = this.chartColorService.resolvedColors(() => this.doughnutChartEntries().map((entry) => entry.color));
    // segment colors of the stacked bar charts: no due date, included, not included, bonus, not graded, missed
    private readonly barColors = this.chartColorService.resolvedColors(() => [
        GraphColors.LIGHT_GREY,
        GraphColors.GREEN,
        GraphColors.LIGHT_GREY,
        GraphColors.YELLOW,
        GraphColors.BLUE,
        GraphColors.RED,
    ]);

    readonly doughnutData = computed(() => singleSeriesChartData(this.doughnutChartEntries(), this.doughnutColors()));
    readonly doughnutOptions = computed(() =>
        doughnutChartOptions({
            legend: false,
            tooltip: { label: (item) => `${this.translateService.instant(item.label)}: ${item.parsed}` },
        }),
    );

    readonly groupChartData = computed(() => {
        const dataPerGroup = new Map<ExerciseType, ChartData<'bar', number[], string>>();
        for (const [exerciseType, exerciseGroup] of this.ngxExerciseGroups()) {
            dataPerGroup.set(exerciseType, multiSeriesToStackedBarData(exerciseGroup as unknown as ChartMultiSeriesEntry[], this.barColors()));
        }
        return dataPerGroup;
    });
    readonly groupChartOptions = computed(() => {
        const optionsPerGroup = new Map<ExerciseType, ChartOptions<'bar'>>();
        for (const [exerciseType, exerciseGroup] of this.ngxExerciseGroups()) {
            optionsPerGroup.set(
                exerciseType,
                barChartOptions({
                    horizontal: true,
                    stacked: true,
                    legend: false,
                    xAxis: { max: exerciseGroup[0]?.xScaleMax ?? 100 },
                    tooltip: {
                        title: () => '',
                        label: (item) => this.barTooltipLines(item),
                    },
                }),
            );
        }
        return optionsPerGroup;
    });

    readonly gradingScaleExists = signal<boolean>(false);
    readonly isBonus = signal<boolean>(false);
    readonly gradeDTO = signal<GradeDTO | undefined>(undefined);

    // Icons
    faQuestionCircle = faQuestionCircle;
    faClipboard = faClipboard;

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    private readonly controls = viewChild.required<TemplateRef<any>>('controls');
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    ngOnInit() {
        // Note: due to lazy loading and router outlet, we use parent 2x here
        this.paramSubscription = this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course.set(this.courseStorageService.getCourse(this.courseId));
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course.set(course);
            this.onCourseLoad();
        });

        // update titles based on the initial language selection
        this.updateExerciseTitles();

        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            // update titles based on the language changes
            this.updateExerciseTitles();
            this.groupExercisesByType(this.courseExercises);
        });

        this.calculateCourseGrade();
    }

    private updateExerciseTitles() {
        this.exerciseTitles.set(ExerciseType.QUIZ, new ExerciseTitle(this.translateService.instant('artemisApp.course.quizExercises'), QUIZ_EXERCISE_COLOR));
        this.exerciseTitles.set(ExerciseType.MODELING, new ExerciseTitle(this.translateService.instant('artemisApp.course.modelingExercises'), MODELING_EXERCISE_COLOR));
        this.exerciseTitles.set(ExerciseType.PROGRAMMING, new ExerciseTitle(this.translateService.instant('artemisApp.course.programmingExercises'), PROGRAMMING_EXERCISE_COLOR));
        this.exerciseTitles.set(ExerciseType.TEXT, new ExerciseTitle(this.translateService.instant('artemisApp.course.textExercises'), TEXT_EXERCISE_COLOR));
        this.exerciseTitles.set(ExerciseType.FILE_UPLOAD, new ExerciseTitle(this.translateService.instant('artemisApp.course.fileUploadExercises'), FILE_UPLOAD_EXERCISE_COLOR));
    }

    ngAfterViewInit() {
        // Send our controls template to parent so it will be rendered in the top bar
        const controls = this.controls();
        if (controls) {
            this.controlConfiguration.subject!.next(controls);
            this.controlsRendered.emit();
        }
    }

    ngOnDestroy() {
        this.translateSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
    }

    private calculateCourseGrade(): void {
        this.gradingService.matchPercentageToGradeStep(this.totalRelativeScore(), this.courseId).subscribe((gradeDTO) => {
            if (gradeDTO) {
                this.gradingScaleExists.set(true);
                this.gradeDTO.set(gradeDTO);
                this.isBonus.set(gradeDTO.gradeType === GradeType.BONUS);
            }
        });
    }

    private onCourseLoad(): void {
        const course = this.course();
        if (course?.exercises) {
            this.courseExercises = course.exercises;
            this.calculateAndFilterNotIncludedInScore();
            this.calculateMaxPoints();
            this.calculateReachablePoints();
            this.calculateReachablePresentationPoints();
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
     */
    private groupExercisesByType(exercises: Exercise[]): void {
        // this reset is now necessary because of the filtering option that triggers the grouping again.
        this.exerciseGroupsInProgress = new Map<ExerciseType, NgxExercise[]>();
        Object.values(ExerciseType).forEach((exerciseType) => {
            this.exerciseGroupsInProgress.set(exerciseType, []);
            this.presentationScoreEnabled.set(exerciseType, false);
        });

        const course = this.course();
        // adding several years to be sure that exercises without due date are sorted at the end. this is necessary for the order inside the statistic charts
        exercises = sortBy(exercises, [(exercise: Exercise) => (exercise.dueDate || dayjs().add(5, 'year')).valueOf()]);
        exercises.forEach((exercise) => {
            if (!exercise.dueDate || exercise.dueDate.isBefore(dayjs()) || exercise.type === ExerciseType.PROGRAMMING) {
                const series = CourseStatisticsComponent.generateDefaultSeries();

                if (!exercise.studentParticipations || exercise.studentParticipations.length === 0) {
                    // 5 = MISSED
                    series[5].value = 100;
                    series[5].afterDueDate = false;
                    series[5].notParticipated = true;
                    series[5].exerciseTitle = exercise.title;
                    series[5].exerciseId = exercise.id!;
                    this.pushToData(exercise, series);
                } else {
                    exercise.studentParticipations.forEach((participation: StudentParticipation) => {
                        const results = getAllResultsOfAllSubmissions(participation.submissions);
                        if (participation.id && results.length) {
                            const participationResult: ParticipationResultDTO | undefined = this.scoresStorageService.getStoredParticipationResult(participation.id);
                            if (participationResult?.rated) {
                                const roundedParticipationScore = roundValueSpecifiedByCourseSettings(participationResult.score!, course);
                                const cappedParticipationScore = Math.min(roundedParticipationScore, 100);
                                const roundedParticipationPoints = roundValueSpecifiedByCourseSettings((participationResult.score! * exercise.maxPoints!) / 100, course);
                                const missedScore = roundValueSpecifiedByCourseSettings(100 - cappedParticipationScore, course);
                                const missedPoints = roundValueSpecifiedByCourseSettings(Math.max(exercise.maxPoints! - roundedParticipationPoints, 0), course);
                                // 5 = MISSED
                                series[5].value = missedScore;
                                series[5].absoluteValue = missedPoints;
                                series[5].afterDueDate = false;
                                series[5].notParticipated = false;
                                series[5].exerciseId = exercise.id!;

                                this.identifyBar(exercise, series, roundedParticipationScore, roundedParticipationPoints);
                                this.pushToData(exercise, series);
                            }
                        } else {
                            if (
                                participation.initializationState === InitializationState.FINISHED &&
                                (!exercise.dueDate || participation.initializationDate?.isBefore(exercise.dueDate!))
                            ) {
                                // 4 = NOT_GRADED
                                series[4].value = 100;
                                series[4].exerciseTitle = exercise.title;
                                series[4].exerciseId = exercise.id!;
                                this.pushToData(exercise, series);
                            } else {
                                // 5 = MISSED
                                series[5].value = 100;
                                // If the user only presses "start exercise", there is still no participation
                                if (participation.initializationState === InitializationState.INITIALIZED) {
                                    series[5].afterDueDate = false;
                                    series[5].notParticipated = true;
                                } else {
                                    series[5].afterDueDate = true;
                                }
                                series[5].exerciseTitle = exercise.title;
                                series[5].exerciseId = exercise.id!;
                                this.pushToData(exercise, series);
                            }
                        }
                    });
                }
            }
        });
        this.pushExerciseGroupsToData();
        this.ngxExerciseGroups.set(this.exerciseGroupsInProgress);
    }

    toggleNotIncludedInScoreExercises() {
        if (this.currentlyHidingNotIncludedInScoreExercises()) {
            this.courseExercises = this.courseExercises.concat(this.courseExercisesNotIncludedInScore);
            this.filteredExerciseIDs.set([]);
        } else {
            this.courseExercises = this.courseExercises.filter((exercise) => !this.courseExercisesNotIncludedInScore.includes(exercise));
            this.filteredExerciseIDs.set(this.courseExercisesNotIncludedInScore.map((exercise) => exercise.id!));
        }
        this.currentlyHidingNotIncludedInScoreExercises.update((value) => !value);
        this.categoryFilter.setupCategoryFilter(this.courseExercises);

        this.groupExercisesByType(this.courseExercises);
    }

    /**
     * Generates array containing default configuration for every possible part in one stacked bar
     * @returns dedicated object that is requested by ngx-charts in order to visualize one bar in the horizontal bar chart
     */
    private static generateDefaultSeries(): Series[] {
        return [
            new Series(ChartBarTitle.NO_DUE_DATE),
            new Series(ChartBarTitle.INCLUDED),
            new Series(ChartBarTitle.NOT_INCLUDED),
            new Series(ChartBarTitle.BONUS),
            new Series(ChartBarTitle.NOT_GRADED),
            new Series(ChartBarTitle.MISSED),
        ];
    }

    /**
     * Retrieve absolute score for each exercise group in the course from the scores storage service and add it to the doughnut chart
     */
    private calculateAbsoluteScores(): void {
        const quizzesTotalScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.QUIZ, ScoreType.ABSOLUTE_SCORE);
        const programmingExerciseTotalScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.PROGRAMMING, ScoreType.ABSOLUTE_SCORE);
        const modelingExerciseTotalScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.MODELING, ScoreType.ABSOLUTE_SCORE);
        const textExerciseTotalScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.TEXT, ScoreType.ABSOLUTE_SCORE);
        const fileUploadExerciseTotalScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.FILE_UPLOAD, ScoreType.ABSOLUTE_SCORE);
        const course = this.course();
        this.overallPoints.set(this.retrieveTotalScoreByScoreType(ScoreType.ABSOLUTE_SCORE));
        const totalPresentationPoints = course?.presentationScore ? 0 : this.retrieveTotalScoreByScoreType(ScoreType.PRESENTATION_SCORE);
        let totalMissedPoints = this.reachablePoints() - this.overallPoints();
        if (totalMissedPoints < 0) {
            totalMissedPoints = 0;
        }

        const scores = [
            programmingExerciseTotalScore,
            quizzesTotalScore,
            modelingExerciseTotalScore,
            textExerciseTotalScore,
            fileUploadExerciseTotalScore,
            totalPresentationPoints,
            totalMissedPoints,
        ];

        this.overallPointsPerExercise.set(ExerciseType.QUIZ, quizzesTotalScore);
        this.overallPointsPerExercise.set(ExerciseType.PROGRAMMING, programmingExerciseTotalScore);
        this.overallPointsPerExercise.set(ExerciseType.MODELING, modelingExerciseTotalScore);
        this.overallPointsPerExercise.set(ExerciseType.TEXT, textExerciseTotalScore);
        this.overallPointsPerExercise.set(ExerciseType.FILE_UPLOAD, fileUploadExerciseTotalScore);
        const doughnutEntriesTemp: YourOverallPointsEntry[] = [];
        scores.forEach((score, index) => {
            if (score > 0) {
                doughnutEntriesTemp.push({
                    name: 'artemisApp.courseOverview.statistics.' + this.labels[index],
                    value: this.roundScoreSpecifiedByCourseSettings(score, course),
                    color: this.doughnutChartColors[index],
                });
            }
        });

        this.doughnutChartEntries.set(doughnutEntriesTemp);
    }

    /**
     * Retrieves the maximum of points for the course from the scores storage service.
     */
    private calculateMaxPoints(): void {
        const quizzesTotalMaxPoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.QUIZ, ScoreType.MAX_POINTS);
        const programmingExerciseTotalMaxPoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.PROGRAMMING, ScoreType.MAX_POINTS);
        const modelingExerciseTotalMaxPoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.MODELING, ScoreType.MAX_POINTS);
        const textExerciseTotalMaxPoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.TEXT, ScoreType.MAX_POINTS);
        const fileUploadExerciseTotalMaxPoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.FILE_UPLOAD, ScoreType.MAX_POINTS);
        this.overallMaxPointsPerExercise.set(ExerciseType.QUIZ, quizzesTotalMaxPoints);
        this.overallMaxPointsPerExercise.set(ExerciseType.PROGRAMMING, programmingExerciseTotalMaxPoints);
        this.overallMaxPointsPerExercise.set(ExerciseType.MODELING, modelingExerciseTotalMaxPoints);
        this.overallMaxPointsPerExercise.set(ExerciseType.TEXT, textExerciseTotalMaxPoints);
        this.overallMaxPointsPerExercise.set(ExerciseType.FILE_UPLOAD, fileUploadExerciseTotalMaxPoints);
        this.overallMaxPoints.set(this.retrieveTotalScoreByScoreType(ScoreType.MAX_POINTS));
    }

    /**
     * Retrieve the relative score for each exercise group in the course from the scores storage service
     */
    private calculateRelativeScores(): void {
        const quizzesRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.QUIZ, ScoreType.RELATIVE_SCORE);
        const programmingExerciseRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.PROGRAMMING, ScoreType.RELATIVE_SCORE);
        const modelingExerciseRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.MODELING, ScoreType.RELATIVE_SCORE);
        const textExerciseRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.TEXT, ScoreType.RELATIVE_SCORE);
        const fileUploadExerciseRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.FILE_UPLOAD, ScoreType.RELATIVE_SCORE);
        this.relativeScoresPerExercise.set(ExerciseType.QUIZ, quizzesRelativeScore);
        this.relativeScoresPerExercise.set(ExerciseType.PROGRAMMING, programmingExerciseRelativeScore);
        this.relativeScoresPerExercise.set(ExerciseType.MODELING, modelingExerciseRelativeScore);
        this.relativeScoresPerExercise.set(ExerciseType.TEXT, textExerciseRelativeScore);
        this.relativeScoresPerExercise.set(ExerciseType.FILE_UPLOAD, fileUploadExerciseRelativeScore);
        this.totalRelativeScore.set(this.retrieveTotalScoreByScoreType(ScoreType.RELATIVE_SCORE));
    }

    /**
     * Retrieve the reachable points for the course from the scores storage service.
     */
    private calculateReachablePoints(): void {
        const quizzesReachablePoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.QUIZ, ScoreType.REACHABLE_POINTS);
        const programmingExercisesReachablePoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.PROGRAMMING, ScoreType.REACHABLE_POINTS);
        const modelingExercisesReachablePoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.MODELING, ScoreType.REACHABLE_POINTS);
        const textExercisesReachablePoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.TEXT, ScoreType.REACHABLE_POINTS);
        const fileUploadExercisesReachablePoints = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.FILE_UPLOAD, ScoreType.REACHABLE_POINTS);
        this.reachablePointsPerExercise.set(ExerciseType.QUIZ, quizzesReachablePoints);
        this.reachablePointsPerExercise.set(ExerciseType.PROGRAMMING, programmingExercisesReachablePoints);
        this.reachablePointsPerExercise.set(ExerciseType.MODELING, modelingExercisesReachablePoints);
        this.reachablePointsPerExercise.set(ExerciseType.TEXT, textExercisesReachablePoints);
        this.reachablePointsPerExercise.set(ExerciseType.FILE_UPLOAD, fileUploadExercisesReachablePoints);
        this.reachablePoints.set(this.retrieveTotalScoreByScoreType(ScoreType.REACHABLE_POINTS));
    }

    /**
     * Retrieve the current relative score for the course from the scores storage service.
     */
    private calculateCurrentRelativeScores(): void {
        const quizzesCurrentRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.QUIZ, ScoreType.CURRENT_RELATIVE_SCORE);
        const programmingExerciseCurrentRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.PROGRAMMING, ScoreType.CURRENT_RELATIVE_SCORE);
        const modelingExerciseCurrentRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.MODELING, ScoreType.CURRENT_RELATIVE_SCORE);
        const textExerciseCurrentRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.TEXT, ScoreType.CURRENT_RELATIVE_SCORE);
        const fileUploadExerciseCurrentRelativeScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.FILE_UPLOAD, ScoreType.CURRENT_RELATIVE_SCORE);
        this.currentRelativeScoresPerExercise.set(ExerciseType.QUIZ, quizzesCurrentRelativeScore);
        this.currentRelativeScoresPerExercise.set(ExerciseType.PROGRAMMING, programmingExerciseCurrentRelativeScore);
        this.currentRelativeScoresPerExercise.set(ExerciseType.MODELING, modelingExerciseCurrentRelativeScore);
        this.currentRelativeScoresPerExercise.set(ExerciseType.TEXT, textExerciseCurrentRelativeScore);
        this.currentRelativeScoresPerExercise.set(ExerciseType.FILE_UPLOAD, fileUploadExerciseCurrentRelativeScore);
        this.currentRelativeScore.set(this.retrieveTotalScoreByScoreType(ScoreType.CURRENT_RELATIVE_SCORE));
    }

    /**
     * Retrieve the presentation score for the course from the scores storage service
     */
    private calculatePresentationScores(): void {
        const programmingExercisePresentationScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.PROGRAMMING, ScoreType.PRESENTATION_SCORE);
        const modelingExercisePresentationScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.MODELING, ScoreType.PRESENTATION_SCORE);
        const textExercisePresentationScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.TEXT, ScoreType.PRESENTATION_SCORE);
        const fileUploadExercisePresentationScore = this.retrieveScoreByExerciseTypeAndScoreType(ExerciseType.FILE_UPLOAD, ScoreType.PRESENTATION_SCORE);
        this.presentationScoresPerExercise.set(ExerciseType.QUIZ, 0);
        this.presentationScoresPerExercise.set(ExerciseType.PROGRAMMING, programmingExercisePresentationScore);
        this.presentationScoresPerExercise.set(ExerciseType.MODELING, modelingExercisePresentationScore);
        this.presentationScoresPerExercise.set(ExerciseType.TEXT, textExercisePresentationScore);
        this.presentationScoresPerExercise.set(ExerciseType.FILE_UPLOAD, fileUploadExercisePresentationScore);
        this.overallPresentationScore.set(this.retrieveTotalScoreByScoreType(ScoreType.PRESENTATION_SCORE));
    }

    /**
     * Retrieve the reachable presentation score for the course from the scores storage service
     */
    private calculateReachablePresentationPoints(): void {
        this.reachablePresentationPoints.set(this.retrieveTotalScoreByScoreType(ScoreType.REACHABLE_PRESENTATION_POINTS));
    }

    /**
     * Retrieves the score for a given score type and exercise type from the scores storage service. Scores are calculated in the server when fetching all courses.
     * @param exerciseType the exercise type for which the score should be retrieved. Must be an element of {Programming, Modeling, Quiz, Text, File upload}.
     * @param scoreType which type of score should be retrieved from the store. Element of {'absoluteScore', 'maxPoints', 'currentRelativeScore', 'presentationScore', 'reachablePoints', 'relativeScore'}
     * @returns requested score value
     */
    private retrieveScoreByExerciseTypeAndScoreType(exerciseType: ExerciseType, scoreType: ScoreType): number {
        const scoresPerExerciseTypeForCourse: ScoresPerExerciseType | undefined = this.scoresStorageService.getStoredScoresPerExerciseType(this.courseId);
        const scoresOfExerciseType: CourseScores | undefined = scoresPerExerciseTypeForCourse ? scoresPerExerciseTypeForCourse.get(exerciseType) : undefined;
        return this.getScoreByScoreType(scoresOfExerciseType, scoreType);
    }

    /**
     * Retrieves the score for an arbitrary score type for the total scores from the scores storage service. Scores are calculated in the server when fetching all courses.
     * @param scoreType which type of score should be retrieved from the store. Element of {'absoluteScore', 'maxPoints', 'currentRelativeScore', 'presentationScore', 'reachablePoints', 'relativeScore'}
     * @returns requested score value
     */
    private retrieveTotalScoreByScoreType(scoreType: ScoreType): number {
        const totalScores: CourseScores | undefined = this.scoresStorageService.getStoredTotalScores(this.courseId);
        return this.getScoreByScoreType(totalScores, scoreType);
    }

    // Retrieve the score for a specific ScoreType from the CourseScores object.
    // The MAX_POINTS and REACHABLE_POINTS belong to the course.
    // All other ScoreTypes inform about the student's personal score and are stored in the StudentScores object.
    private getScoreByScoreType(scores: CourseScores | undefined, scoreType: ScoreType): number {
        if (!scores) {
            return NaN;
        }

        switch (scoreType) {
            case ScoreType.MAX_POINTS:
                return scores.maxPoints;
            case ScoreType.REACHABLE_POINTS:
                return scores.reachablePoints;
            case ScoreType.ABSOLUTE_SCORE:
                return scores.studentScores.absoluteScore;
            case ScoreType.RELATIVE_SCORE:
                return scores.studentScores.relativeScore;
            case ScoreType.CURRENT_RELATIVE_SCORE:
                return scores.studentScores.currentRelativeScore;
            case ScoreType.PRESENTATION_SCORE:
                return scores.studentScores.presentationScore;
            case ScoreType.REACHABLE_PRESENTATION_POINTS:
                return scores.reachablePresentationPoints;
        }
    }

    calculateAndFilterNotIncludedInScore() {
        this.currentlyHidingNotIncludedInScoreExercises.set(true);
        this.courseExercisesNotIncludedInScore = this.courseExercises.filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED);
        this.courseExercises = this.courseExercises.filter((exercise) => !this.courseExercisesNotIncludedInScore.includes(exercise));
        this.courseExercisesFilteredByCategories = this.courseExercises;
        this.filteredExerciseIDs.set(this.courseExercisesNotIncludedInScore.map((exercise) => exercise.id!));
        this.categoryFilter.setupCategoryFilter(this.courseExercises);
        this.calculateNumberOfAppliedFilters();
    }

    /**
     * Depending on the type of the exercise, it adds a new object containing
     * the different scores of the corresponding exercise group of the chart
     * @param exercise an arbitrary exercise of a course
     * @param allSeries an array of dedicated objects containing the students' performance in this exercise that is visualized by the chart
     */
    private pushToData(exercise: Exercise, allSeries: Series[]): void {
        const exerciseType = exercise.type!;
        const ngxExercise = new NgxExercise(exercise.title, allSeries, exerciseType);
        this.exerciseGroupsInProgress.get(exerciseType)!.push(ngxExercise);
        this.presentationScoreEnabled.set(exerciseType, (this.presentationScoreEnabled.get(exerciseType) ?? false) || (exercise.presentationScoreEnabled ?? false));
        if (exerciseType == ExerciseType.PROGRAMMING) {
            allSeries.forEach((series: Series) => {
                series.isProgrammingExercise = true;
            });
        }
    }

    /**
     * Adds some metadata to every non-empty exercise group and pushes it to ngxExerciseGroups
     */
    private pushExerciseGroupsToData(): void {
        Object.values(ExerciseType).forEach((exerciseType) => {
            const exerciseGroup = this.exerciseGroupsInProgress.get(exerciseType)!;
            if (exerciseGroup.length > 0) {
                const firstExerciseGroup = exerciseGroup[0];
                firstExerciseGroup.absoluteScore = this.overallPointsPerExercise.get(exerciseType)!;
                firstExerciseGroup.relativeScore = this.relativeScoresPerExercise.get(exerciseType)!;
                firstExerciseGroup.reachablePoints = this.reachablePointsPerExercise.get(exerciseType)!;
                firstExerciseGroup.currentRelativeScore = this.currentRelativeScoresPerExercise.get(exerciseType)!;
                firstExerciseGroup.overallMaxPoints = this.overallMaxPointsPerExercise.get(exerciseType)!;
                firstExerciseGroup.presentationScore = this.presentationScoresPerExercise.get(exerciseType)!;
                firstExerciseGroup.presentationScoreEnabled = this.presentationScoreEnabled.get(exerciseType)!;
                firstExerciseGroup.xScaleMax = this.setXScaleMax(exerciseGroup);
                firstExerciseGroup.height = this.calculateChartHeight(exerciseGroup.length);
            } else {
                // prevent an error in html when there is no exercise of one specific type
                this.exerciseGroupsInProgress.delete(exerciseType);
            }
        });
    }

    /**
     * Depending on if the exercise has a due date and how its score is included,
     * adds the student score to the corresponding bar.
     * @param exercise the exercise of interest which has to be displayed by the chart
     * @param series the series the students score gets pushed to
     * @param roundedParticipationScore the students relative score
     * @param split the students absolute score
     */
    private identifyBar(exercise: Exercise, series: Series[], roundedParticipationScore: number, split: number): void {
        // the bar on index 0 is only rendered if the exercise has no due date
        let index = 0;
        if (exercise.dueDate) {
            const scoreTypes = [IncludedInOverallScore.INCLUDED_COMPLETELY, IncludedInOverallScore.NOT_INCLUDED, IncludedInOverallScore.INCLUDED_AS_BONUS];
            // we shift the index by 1, because index 0 is accessed if the exercise has no due date and this case is not represented in scoreTypes
            index = scoreTypes.indexOf(exercise.includedInOverallScore!) + 1;
        }
        series[index].value = roundedParticipationScore;
        series[index].absoluteValue = split;
        series[index].exerciseId = exercise.id!;
    }

    /**
     * Sets the maximum scale on the x-axis if there are exercises with > 100%
     * @param exerciseGroup the exercise group
     * @returns maximum value visible on xAxis
     */
    private setXScaleMax(exerciseGroup: NgxExercise[]): number {
        let xScaleMax = 100;
        exerciseGroup.forEach((exercise: NgxExercise) => {
            const maxScore = Math.max(exercise.series[0].value, exercise.series[1].value, exercise.series[2].value, exercise.series[3].value);
            xScaleMax = xScaleMax > maxScore ? xScaleMax : Math.ceil(maxScore);
        });
        return xScaleMax;
    }

    /**
     * Handles the event fired if the user clicks on an arbitrary bar in the stacked bar charts.
     * Delegates the user to the corresponding exercise detail page in a new tab
     * @param event the event that is fired by p-chart
     * @param exerciseType the exercise group whose chart was clicked
     */
    onSelect(event: any, exerciseType: ExerciseType) {
        const chartData = this.groupChartData().get(exerciseType);
        if (!chartData) {
            return;
        }
        const selected = toChartSelectEvent(event, chartData);
        const exerciseId = selected?.meta?.['exerciseId'];
        if (exerciseId === undefined) {
            return;
        }
        this.navigationUtilService.routeInNewTab(['courses', this.course()!.id!, 'exercises', exerciseId]);
    }

    /**
     * Builds the tooltip lines for one segment of a stacked bar chart, depending on the segment type
     * and the properties of the corresponding exercise (mirrors the former ngx-charts tooltip template).
     * @param item the hovered tooltip item provided by chart.js
     */
    private barTooltipLines(item: TooltipItem<'bar'>): string[] {
        const series = item.dataset.meta?.[item.dataIndex] as Series | undefined;
        if (!series) {
            return [];
        }
        const lines: string[] = [];
        const percentage = this.roundScoreSpecifiedByCourseSettings(series.value, this.course());
        if ([ChartBarTitle.INCLUDED, ChartBarTitle.BONUS, ChartBarTitle.NOT_INCLUDED, ChartBarTitle.NO_DUE_DATE].includes(series.name)) {
            let achievedLine: string;
            if (series.isProgrammingExercise) {
                achievedLine = this.translateService.instant('artemisApp.courseOverview.statistics.programmingExercisePassedTests', {
                    tests: series.absoluteValue,
                    percentage,
                });
            } else {
                achievedLine = this.translateService.instant('artemisApp.courseOverview.statistics.exerciseAchievedScore', {
                    points: series.absoluteValue,
                    percentage,
                });
            }
            if (series.name === ChartBarTitle.BONUS) {
                achievedLine += ' | ' + this.translateService.instant('artemisApp.courseOverview.statistics.bonusPointTooltip');
            }
            if (series.name === ChartBarTitle.NOT_INCLUDED) {
                achievedLine += ' | ' + this.translateService.instant('artemisApp.courseOverview.statistics.notIncludedTooltip');
            }
            lines.push(achievedLine);
        }
        if (series.name === ChartBarTitle.MISSED) {
            if (!series.afterDueDate && !series.notParticipated && !series.isProgrammingExercise) {
                lines.push(this.translateService.instant('artemisApp.courseOverview.statistics.exerciseMissedScore', { points: series.absoluteValue, percentage }));
            }
            if (series.notParticipated) {
                lines.push(this.translateService.instant('artemisApp.courseOverview.statistics.exerciseNotParticipated', { exercise: series.exerciseTitle }));
            }
            if (series.afterDueDate) {
                lines.push(this.translateService.instant('artemisApp.courseOverview.statistics.exerciseParticipatedAfterDueDate', { exercise: series.exerciseTitle }));
            }
            if (series.isProgrammingExercise && !series.notParticipated) {
                lines.push(this.translateService.instant('artemisApp.courseOverview.statistics.programmingExerciseFailedTests', { tests: series.absoluteValue, percentage }));
            }
        } else if (series.name === ChartBarTitle.NOT_GRADED) {
            lines.push(this.translateService.instant('artemisApp.courseOverview.statistics.exerciseNotGraded', { exercise: series.exerciseTitle }));
        }
        return lines;
    }

    /**
     * Handles the selection or deselection of a specific category and configures the filter accordingly
     * @param category the category that is selected or deselected
     */
    toggleCategory(category: string) {
        const isIncluded = this.categoryFilter.getCurrentFilterState(category)!;
        this.courseExercisesFilteredByCategories = this.categoryFilter.toggleCategory<Exercise>(this.courseExercises, category);
        this.setupFilteredChart(!isIncluded);
    }

    /**
     * Handles the use case when the user selects or deselects the option "select all categories"
     */
    toggleAllCategories(): void {
        this.courseExercisesFilteredByCategories = this.categoryFilter.toggleAllCategories<Exercise>(this.courseExercises);
        this.setupFilteredChart(this.categoryFilter.allCategoriesSelected);
    }

    /**
     * handles the selection and deselection of "exercises with no categories" filter option
     */
    toggleExercisesWithNoCategory(): void {
        this.courseExercisesFilteredByCategories = this.categoryFilter.toggleExercisesWithNoCategory<Exercise>(this.courseExercises);
        this.setupFilteredChart(this.categoryFilter.includeExercisesWithNoCategory);
    }

    /**
     * Auxiliary method that updates the filtered exercise IDs. These are necessary in order to update the performance in exercises chart below
     * @param included indicates whether the updated filter is now selected or deselected and updates the filtered exercise IDs accordingly
     */
    private filterExerciseIDsForCategorySelection(included: boolean): void {
        if (!included) {
            const newlyFilteredIDs = this.courseExercises
                .filter((exercise) => !this.courseExercisesFilteredByCategories.includes(exercise))
                .map((exercise) => exercise.id!)
                .filter((id) => !this.filteredExerciseIDs().includes(id));
            this.filteredExerciseIDs.update((ids) => ids.concat(newlyFilteredIDs));
        } else {
            this.filteredExerciseIDs.update((ids) => ids.filter((id) => !this.courseExercisesFilteredByCategories.find((exercise) => exercise.id === id)));
        }
    }

    private calculateNumberOfAppliedFilters(): void {
        this.numberOfAppliedFilters.set(this.categoryFilter.numberOfActiveFilters + (this.currentlyHidingNotIncludedInScoreExercises() ? 1 : 0));
    }

    /**
     * Determines and returns the height of the whole chart depending of the amount of its entries
     * @param chartEntries the amount of chart entries
     */
    private calculateChartHeight(chartEntries: number): number {
        /*
        Each chart bar should have a height of 45px
        Furthermore we have to take the bar padding between the bars into account
        Finally, we need to add space for the x-axis and its ticks
         */
        return chartEntries * this.CHART_HEIGHT + this.BAR_PADDING * (chartEntries - 1) + this.DEFAULT_SIZE;
    }

    /**
     * Auxiliary method to reduce code duplication
     * Calculates the number of applied filters, groups the updated set of exercises and updates the set of filtered IDs
     * @param isIncluded indicates whether the updated filter is now selected or deselected and updates the filtered exercise IDs accordingly
     */
    private setupFilteredChart(isIncluded: boolean) {
        this.calculateNumberOfAppliedFilters();
        this.groupExercisesByType(this.courseExercisesFilteredByCategories);
        this.filterExerciseIDsForCategorySelection(isIncluded);
    }
}
