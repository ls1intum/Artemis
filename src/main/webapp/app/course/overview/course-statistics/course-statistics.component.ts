import { AfterViewInit, Component, EventEmitter, OnDestroy, OnInit, TemplateRef, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faClipboard, faFilter, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore, ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradeDTO } from 'app/assessment/shared/entities/grade-step.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared-ui/tab-bar/tab-bar';
import { ChartCategoryFilter } from 'app/exercise/chart/chart-category-filter';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { singleSeriesChart } from 'app/shared-ui/chart/tum-aet-ui-chart-adapters';
import { ScoreType } from 'app/foundation/constants/score-type.constants';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import dayjs from 'dayjs/esm';
import { Subject, Subscription, distinctUntilChanged, map } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { CourseStatisticsService, LectureLearningStatistics } from './course-statistics.service';
import { CourseStatisticsResultsComponent } from './course-statistics-results.component';
import { CourseStatisticsPerformanceComponent } from './course-statistics-performance.component';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CourseCompetency, CourseCompetencyType, getMastery, getProgress } from 'app/atlas/shared/entities/competency.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { hasSubmittedForStatistics } from './course-statistics.utils';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDoughnutChartComponent,
    TumAetUiDoughnutChartConfig,
    TumAetUiMessageComponent,
    TumAetUiProgressBarComponent,
    TumAetUiTableDirective,
} from '@tumaet/ui-angular';

const QUIZ_EXERCISE_COLOR = 'var(--statistics-score-quiz-color)';
const PROGRAMMING_EXERCISE_COLOR = 'var(--statistics-score-programming-color)';
const MODELING_EXERCISE_COLOR = 'var(--statistics-score-modeling-color)';
const TEXT_EXERCISE_COLOR = 'var(--statistics-score-text-color)';
const FILE_UPLOAD_EXERCISE_COLOR = 'var(--statistics-score-file-upload-color)';

interface YourOverallPointsEntry extends ChartSeriesEntry {
    color: string;
}

@Component({
    selector: 'jhi-course-statistics',
    templateUrl: './course-statistics.component.html',
    styleUrl: './course-statistics.component.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        TumAetUiDoughnutChartComponent,
        RouterLink,
        ArtemisTranslatePipe,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiMessageComponent,
        TumAetUiProgressBarComponent,
        TumAetUiTableDirective,
        CourseStatisticsResultsComponent,
        CourseStatisticsPerformanceComponent,
    ],
})
export class CourseStatisticsComponent implements OnInit, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    private courseStorageService = inject(CourseStorageService);
    private courseOverviewExercisesService = inject(CourseOverviewExercisesService);
    private courseTabRefreshService = inject(CourseTabRefreshService);
    private scoresStorageService = inject(ScoresStorageService);
    private translateService = inject(TranslateService);
    private route = inject(ActivatedRoute);
    private gradingService = inject(GradingService);
    categoryFilter = inject(ChartCategoryFilter);
    private readonly statisticsService = inject(CourseStatisticsService);
    private readonly availableTabsService = inject(CourseAvailableTabsService);
    private readonly competencyService = inject(CourseCompetencyService);
    private progressSubscription?: Subscription;

    readonly showFilters = signal(false);
    readonly activeCourseId = signal(0);
    readonly pointBreakdown = signal<{ type: ExerciseType; achieved: number; attainable: number; total: number }[]>([]);
    readonly refresh = signal(0);
    readonly materialStatistics = signal<LectureLearningStatistics[]>([]);
    readonly materialsAvailable = signal(false);
    readonly materialsLoading = signal(false);
    readonly materialsFailed = signal(false);
    readonly competenciesAvailable = signal(false);
    readonly competenciesLoading = signal(false);
    readonly competenciesFailed = signal(false);
    readonly competencies = signal<CourseCompetency[]>([]);
    readonly materialTypes = ['attachment', 'text', 'online'] as const;
    readonly releasedExercises = computed(() => (this.course()?.exercises ?? []).filter((exercise) => !exercise.releaseDate?.isAfter(dayjs())));
    readonly resultExercises = computed(() => this.releasedExercises().filter((exercise) => !this.filteredExerciseIDs().includes(exercise.id!)));
    readonly submittedExerciseCount = computed(
        () =>
            this.releasedExercises().filter((exercise) =>
                exercise.studentParticipations?.some((participation) => !isPracticeMode(participation) && hasSubmittedForStatistics(participation)),
            ).length,
    );
    readonly materialTotal = computed(() => this.materialStatistics().reduce((total, row) => total + row.total, 0));
    readonly showMaterials = computed(() => this.materialsAvailable() && (this.materialsLoading() || this.materialsFailed() || this.materialTotal() > 0));
    readonly showCompetencies = computed(() => this.competenciesAvailable() && (this.competenciesLoading() || this.competenciesFailed() || this.competencies().length > 0));
    readonly materialCompleted = computed(() => this.materialStatistics().reduce((total, row) => total + row.completed, 0));
    readonly materialBreakdown = computed(() =>
        this.materialTypes
            .map((type) => ({
                type,
                total: this.materialStatistics()
                    .filter((row) => row.type === type)
                    .reduce((total, row) => total + row.total, 0),
                completed: this.materialStatistics()
                    .filter((row) => row.type === type)
                    .reduce((total, row) => total + row.completed, 0),
            }))
            .filter((row) => row.total > 0),
    );
    /** Derive display metrics once per competency update rather than during template rendering. */
    readonly competencyRows = computed(() =>
        this.competencies().map((competency) => ({
            competency,
            progress: getProgress(competency.userProgress?.[0]),
            mastery: getMastery(competency.userProgress?.[0]),
            threshold: competency.masteryThreshold ?? 100,
        })),
    );
    readonly masteredCompetencies = computed(() => this.competencyRows().filter((row) => row.mastery >= row.threshold).length);

    private safeScore(score: number): number {
        return Number.isFinite(score) ? score : 0;
    }
    toggleFilters(): void {
        this.showFilters.update((value) => !value);
    }

    loadLearningStatistics(): void {
        this.progressSubscription?.unsubscribe();
        const subscriptions = new Subscription();
        this.progressSubscription = subscriptions;
        const courseId = this.courseId;
        subscriptions.add(
            this.availableTabsService.loadIfNeeded(courseId).subscribe({
                next: (tabs) => {
                    this.materialsAvailable.set(tabs.lectures);
                    this.competenciesAvailable.set(tabs.competencies);
                    if (tabs.lectures) {
                        this.materialsLoading.set(true);
                        this.materialsFailed.set(false);
                        subscriptions.add(
                            this.statisticsService.getLearningStatistics(courseId).subscribe({
                                next: (statistics) => {
                                    this.materialStatistics.set(statistics);
                                    this.materialsLoading.set(false);
                                },
                                error: () => {
                                    this.materialsFailed.set(true);
                                    this.materialsLoading.set(false);
                                },
                            }),
                        );
                    }
                    if (tabs.competencies) {
                        this.competenciesLoading.set(true);
                        this.competenciesFailed.set(false);
                        subscriptions.add(
                            this.competencyService.getAllForCourse(courseId).subscribe({
                                next: (response) => {
                                    this.competencies.set((response.body ?? []).filter((competency) => competency.type === CourseCompetencyType.COMPETENCY));
                                    this.competenciesLoading.set(false);
                                },
                                error: () => {
                                    this.competenciesFailed.set(true);
                                    this.competenciesLoading.set(false);
                                },
                            }),
                        );
                    }
                },
                error: () => {
                    /* The exercise statistics remain usable if optional tab availability cannot be loaded. */
                },
            }),
        );
    }

    controlsRendered = new EventEmitter<void>();
    private tabReselectionSubscription?: Subscription;

    courseId!: number; // set in ngOnInit() from route params
    private courseExercises: Exercise[] = [];
    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private translateSubscription?: Subscription;
    private exercisesLoadSubscription?: Subscription;
    private gradeSubscription?: Subscription;
    readonly course = signal<Course | undefined>(undefined);
    readonly numberOfAppliedFilters = signal<number>(0);

    private courseExercisesNotIncludedInScore: Exercise[] = [];
    private courseExercisesFilteredByCategories: Exercise[] = [];
    readonly currentlyHidingNotIncludedInScoreExercises = signal<boolean>(false);
    readonly filteredExerciseIDs = signal<number[]>([]);
    /** Filter-id updates also mark changes made through the legacy category-filter service. */
    readonly categoryOptions = computed(() => {
        this.filteredExerciseIDs();
        return [...this.categoryFilter.exerciseCategories].map((category) => ({ category, selected: this.categoryFilter.getCurrentFilterState(category) ?? false }));
    });

    // Icons

    // overall points
    readonly overallPoints = signal<number>(0);
    overallPointsPerExercise = new Map<ExerciseType, number>();

    // overall points the student would have if the points of exercise variants were not capped at their group's maxPoints.
    // Only relevant (and shown) when the course has exercise variant groups; see courseHasExerciseVariants.
    readonly overallPointsTotal = signal<number>(0);

    // whether the course contains exercise variant groups with a configured points cap. If so, the statistics page shows
    // both the total (uncapped) and the credited (capped) points to make the variant capping transparent.
    readonly courseHasExerciseVariants = computed<boolean>(() =>
        (this.course()?.exercises ?? []).some((exercise) => exercise.exerciseVariantGroup?.id !== undefined && exercise.exerciseVariantGroup.maxPoints !== undefined),
    );

    // relative score
    readonly totalRelativeScore = signal<number>(0);

    // max points
    readonly overallMaxPoints = signal<number>(0);
    overallMaxPointsPerExercise = new Map<ExerciseType, number>();

    // reachable points
    readonly reachablePoints = signal<number>(0);
    reachablePointsPerExercise = new Map<ExerciseType, number>();

    // current relative score
    readonly currentRelativeScore = signal<number>(0);

    // presentation score
    readonly overallPresentationScore = signal<number>(0);

    // reachable presentation points
    readonly reachablePresentationPoints = signal<number>(0);

    /** Finite course-score values shared by the ring and the scoring details. */
    readonly displayedScores = computed(() => ({
        currentPercentage: this.safeScore(this.currentRelativeScore()),
        creditedPoints: this.safeScore(this.overallPoints()),
        totalEarnedPoints: this.safeScore(this.overallPointsTotal()),
        attainablePoints: this.safeScore(this.reachablePoints()),
        maximumPoints: this.safeScore(this.overallMaxPoints()),
        overallPercentage: this.safeScore(this.totalRelativeScore()),
    }));

    doughnutChartColors: string[] = [
        PROGRAMMING_EXERCISE_COLOR,
        QUIZ_EXERCISE_COLOR,
        MODELING_EXERCISE_COLOR,
        TEXT_EXERCISE_COLOR,
        FILE_UPLOAD_EXERCISE_COLOR,
        GraphColors.LIGHT_BLUE,
        GraphColors.RED,
    ];

    /** Bumped on a language change, so label computeds re-run; the value itself is never read for content. */
    private readonly currentLanguage = signal<string | undefined>(undefined);

    // entries of the "Your overall points" doughnut chart (name is a translation key, color the slice color)
    readonly doughnutChartEntries = signal<YourOverallPointsEntry[]>([]);
    readonly usesCreditedPointsRing = computed(() => this.doughnutChartEntries().some((entry) => entry.name === 'artemisApp.courseOverview.statistics.redesign.points'));

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

    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    // colors of the doughnut chart slices (depend on the displayed entries)
    private readonly doughnutColors = computed(() => this.doughnutChartEntries().map((entry) => entry.color));

    readonly doughnutData = computed(() => {
        // The entries carry translation keys, and the chart renders its labels into the data table a screen
        // reader reads, so they have to be resolved here rather than only in the tooltip. Reading the language
        // keeps them correct after a language switch, which does not rebuild the entries themselves.
        this.currentLanguage();
        const translated = this.doughnutChartEntries().map((entry) => cloneWith(entry, { name: this.translateService.instant(entry.name) }));
        return singleSeriesChart(translated, this.doughnutColors());
    });
    readonly doughnutConfig: TumAetUiDoughnutChartConfig = {
        legend: false,
        tooltip: {
            title: (items) => items[0]?.label ?? '',
            label: (item) => `${item.value}`,
        },
    };

    readonly gradingScaleExists = signal<boolean>(false);
    readonly isBonus = signal<boolean>(false);
    readonly gradeDTO = signal<GradeDTO | undefined>(undefined);

    // Icons
    readonly faFilter = faFilter;
    faQuestionCircle = faQuestionCircle;
    faClipboard = faClipboard;

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    private readonly controls = viewChild.required<TemplateRef<unknown>>('controls');
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<unknown>>(),
    };

    ngOnInit() {
        // update titles based on the initial language selection

        this.translateSubscription = this.translateService.onLangChange.subscribe((event) => {
            // update titles based on the language changes
            this.currentLanguage.set(event.lang);
        });

        // Note: due to lazy loading and router outlet, we use parent 2x here. Angular reuses this component when only
        // the course parameter changes, so every distinct id must cancel and rebuild its course-scoped state.
        this.paramSubscription = this.route.parent?.parent?.params
            .pipe(
                map((params) => parseInt(params['courseId'], 10)),
                distinctUntilChanged(),
            )
            .subscribe((courseId) => this.activateCourse(courseId));

        // Selecting the statistics tab while already on it acts as a refresh
        this.tabReselectionSubscription = this.courseTabRefreshService.reselections(this.route).subscribe(() => this.loadExercises(this.courseId));
    }

    private activateCourse(courseId: number): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.exercisesLoadSubscription?.unsubscribe();
        this.gradeSubscription?.unsubscribe();
        this.resetCourseState();

        this.courseId = courseId;
        this.activeCourseId.set(courseId);
        this.course.set(this.courseStorageService.getCourse(courseId));
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(courseId).subscribe((course: Course) => {
            this.course.set(course);
            this.onCourseLoad();
        });

        // The exercises and their scores are not part of the course itself, so this tab loads them. The result arrives
        // through the course update subscription above and through the ScoresStorageService.
        this.loadExercises(courseId);
    }

    /**
     * Fetches the exercises and their scores without clearing the rendered charts.
     *
     * Also the refresh path: selecting the statistics tab while already on it re-runs this, so newly graded work is
     * reflected without a page reload, and the charts keep showing the previous figures until the new ones arrive.
     *
     * @param courseId the course to load the exercises and scores of
     */
    private loadExercises(courseId: number): void {
        this.refresh.update((value) => value + 1);
        this.loadLearningStatistics();
        this.exercisesLoadSubscription?.unsubscribe();
        this.exercisesLoadSubscription = this.courseOverviewExercisesService.loadIfNeeded(courseId).subscribe(() => {
            this.calculateCourseGrade();
        });
    }

    private resetCourseState(): void {
        this.pointBreakdown.set([]);
        this.progressSubscription?.unsubscribe();
        this.materialStatistics.set([]);
        this.materialsAvailable.set(false);
        this.materialsLoading.set(false);
        this.materialsFailed.set(false);
        this.competencies.set([]);
        this.competenciesAvailable.set(false);
        this.competenciesLoading.set(false);
        this.competenciesFailed.set(false);
        this.course.set(undefined);
        this.courseExercises = [];
        this.courseExercisesNotIncludedInScore = [];
        this.courseExercisesFilteredByCategories = [];
        this.currentlyHidingNotIncludedInScoreExercises.set(false);
        this.filteredExerciseIDs.set([]);
        this.numberOfAppliedFilters.set(0);
        this.categoryFilter.filterMap.clear();
        this.categoryFilter.setupCategoryFilter([]);

        this.overallPoints.set(0);
        this.overallPointsTotal.set(0);
        this.totalRelativeScore.set(0);
        this.overallMaxPoints.set(0);
        this.reachablePoints.set(0);
        this.currentRelativeScore.set(0);
        this.overallPresentationScore.set(0);
        this.reachablePresentationPoints.set(0);
        this.overallPointsPerExercise = new Map();
        this.overallMaxPointsPerExercise = new Map();
        this.reachablePointsPerExercise = new Map();
        this.doughnutChartEntries.set([]);

        this.gradingScaleExists.set(false);
        this.isBonus.set(false);
        this.gradeDTO.set(undefined);
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
        this.progressSubscription?.unsubscribe();
        this.tabReselectionSubscription?.unsubscribe();
        this.translateSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
        this.exercisesLoadSubscription?.unsubscribe();
        this.gradeSubscription?.unsubscribe();
    }

    private calculateCourseGrade(): void {
        this.gradeSubscription?.unsubscribe();
        const relativeScore = this.totalRelativeScore();
        // NaN when no total scores are stored for the course yet; asking the server to match it is meaningless
        if (!Number.isFinite(relativeScore)) {
            return;
        }
        this.gradeSubscription = this.gradingService.matchPercentageToGradeStep(relativeScore, this.courseId).subscribe((gradeDTO) => {
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
            this.pointBreakdown.set(
                [...this.overallMaxPointsPerExercise]
                    .filter(([, total]) => total > 0)
                    .map(([type, total]) => ({
                        type,
                        total,

                        achieved: this.overallPointsPerExercise.get(type) ?? 0,
                        attainable: this.reachablePointsPerExercise.get(type) ?? 0,
                    })),
            );
        }
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
        this.calculateNumberOfAppliedFilters();
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
        this.overallPointsTotal.set(this.retrieveTotalScoreByScoreType(ScoreType.ABSOLUTE_SCORE_TOTAL));
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
        let entries: YourOverallPointsEntry[] = scores.map((score, index) => ({
            name: 'artemisApp.courseOverview.statistics.' + this.labels[index],
            value: score,
            color: this.doughnutChartColors[index],
        }));
        const typePoints = scores
            .slice(0, -1)
            .filter(Number.isFinite)
            .reduce((sum, score) => sum + score, 0);
        const difference = this.roundScoreSpecifiedByCourseSettings(Math.abs(typePoints - this.overallPoints()), course);
        // A cross-type variant cap is applied once course-wide, while the type totals are calculated independently.
        // Do not invent an allocation of that cap between the type slices. Ignore differences within scoring precision.
        if (this.courseHasExerciseVariants() && Number.isFinite(difference) && difference > 0) {
            entries = [
                { name: 'artemisApp.courseOverview.statistics.redesign.points', value: this.overallPoints(), color: 'var(--tumaet-ui-accent-color)' },
                { name: 'artemisApp.courseOverview.statistics.' + this.missingPointsLabel, value: totalMissedPoints, color: this.doughnutChartColors[6] },
            ];
        }
        this.doughnutChartEntries.set(
            entries.filter((entry) => entry.value > 0).map((entry) => cloneWith(entry, { value: this.roundScoreSpecifiedByCourseSettings(entry.value, course) })),
        );
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
        this.currentRelativeScore.set(this.retrieveTotalScoreByScoreType(ScoreType.CURRENT_RELATIVE_SCORE));
    }

    /**
     * Retrieve the presentation score for the course from the scores storage service
     */
    private calculatePresentationScores(): void {
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
            case ScoreType.ABSOLUTE_SCORE_TOTAL:
                return scores.studentScores.absoluteScoreTotal;
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
     * Handles the selection or deselection of a specific category and configures the filter accordingly
     * @param category the category that is selected or deselected
     */
    toggleCategory(category: string) {
        const isIncluded = this.categoryFilter.getCurrentFilterState(category)!;
        this.courseExercisesFilteredByCategories = this.categoryFilter.toggleCategory<Exercise>(this.courseExercises, category);
        this.updateCategoryFilters(!isIncluded);
    }

    /**
     * Handles the use case when the user selects or deselects the option "select all categories"
     */
    toggleAllCategories(): void {
        this.courseExercisesFilteredByCategories = this.categoryFilter.toggleAllCategories<Exercise>(this.courseExercises);
        this.updateCategoryFilters(this.categoryFilter.allCategoriesSelected);
    }

    /**
     * handles the selection and deselection of "exercises with no categories" filter option
     */
    toggleExercisesWithNoCategory(): void {
        this.courseExercisesFilteredByCategories = this.categoryFilter.toggleExercisesWithNoCategory<Exercise>(this.courseExercises);
        this.updateCategoryFilters(this.categoryFilter.includeExercisesWithNoCategory);
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
        const excludedCategories = [...this.categoryFilter.exerciseCategories].filter((category) => !this.categoryFilter.getCurrentFilterState(category)).length;
        const excludedUncategorized = this.categoryFilter.exercisesWithoutCategoriesPresent && !this.categoryFilter.includeExercisesWithNoCategory ? 1 : 0;
        this.numberOfAppliedFilters.set(excludedCategories + excludedUncategorized + (this.currentlyHidingNotIncludedInScoreExercises() ? 1 : 0));
    }

    /**
     * Auxiliary method to reduce code duplication
     * Calculates the number of applied filters, groups the updated set of exercises and updates the set of filtered IDs
     * @param isIncluded indicates whether the updated filter is now selected or deselected and updates the filtered exercise IDs accordingly
     */
    private updateCategoryFilters(isIncluded: boolean) {
        this.calculateNumberOfAppliedFilters();
        this.filterExerciseIDsForCategorySelection(isIncluded);
    }
}
