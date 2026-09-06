import { Component, Injector, OnInit, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { catchError, map, switchMap, take, tap } from 'rxjs/operators';
import { EMPTY, of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/localci/shared/entities/build-log.model';
import { Feedback, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BuildLogService } from 'app/programming/shared/services/build-log.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { isProgrammingExerciseParticipation } from 'app/programming/shared/utils/programming-exercise.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { faCircleNotch, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { axisTickFormattingWithPercentageSign } from 'app/exercise/statistics-graph/util/statistics-graph.utils';
import { Course } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { FeedbackItemService, FeedbackItemServiceImpl } from 'app/exercise/feedback/item/feedback-item-service';
import { ProgrammingFeedbackItemService } from 'app/exercise/feedback/item/programming-feedback-item.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { evaluateTemplateStatus, isOnlyCompilationTested, isStudentParticipation, resultIsPreliminary } from '../result/result.utils';
import { FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { FeedbackChartData } from 'app/exercise/feedback/chart/feedback-chart-data';
import { stackedBarChart } from 'app/shared-ui/chart/tum-ui-chart-adapters';
import { FeedbackChartService } from 'app/exercise/feedback/chart/feedback-chart.service';
import { isFeedbackGroup } from 'app/exercise/feedback/group/feedback-group';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet, UpperCasePipe } from '@angular/common';
import { FeedbackNodeComponent } from './node/feedback-node.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { Participation, getLatestSubmission } from 'app/exercise/shared/entities/participation/participation.model';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { TumUiBarChartComponent, TumUiBarChartConfig } from '@tumaet/ui-angular';

const CODE_REFERENCE_CONTEXT_LINES = 2;
const MAX_DISPLAYED_CODE_REFERENCE_LINES = 50;

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './feedback.component.html',
    imports: [
        TranslateDirective,
        FaIconComponent,
        TumUiBarChartComponent,
        TagModule,
        ButtonModule,
        TooltipModule,
        NgTemplateOutlet,
        FeedbackNodeComponent,
        UpperCasePipe,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class FeedbackComponent implements OnInit {
    private resultService = inject(ResultService);
    private buildLogService = inject(BuildLogService);
    private feedbackService = inject(FeedbackService);
    private feedbackChartService = inject(FeedbackChartService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private injector = inject(Injector);
    readonly dialogRef = inject(DynamicDialogRef, { optional: true });

    readonly BuildLogType = BuildLogType;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly resultIsPreliminary = resultIsPreliminary;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly xAxisFormatting = axisTickFormattingWithPercentageSign;

    private showTestDetails = false;

    // Read-only signal inputs. Supplied either via template bindings ([result], [participation], …) or, when the
    // component is opened through DialogService, via `inputValues` — PrimeNG forwards those with componentRef.setInput,
    // so the same signal inputs serve both paths and no imperative "value + getter/setter" facade is needed.
    readonly result = input.required<Result>();
    readonly participation = input.required<Participation>();
    /**
     * Specify the feedback.testCase.id values that should be shown, all other values will not be visible.
     * Used to show only feedback related to a specific task. Omitted (undefined) in the standalone feedback view.
     */
    readonly feedbackFilter = input<number[] | undefined>(undefined);
    /** Translate key for an HTML message that is displayed at the top of the result details, if defined. */
    readonly messageKey = input<string | undefined>(undefined);
    readonly latestDueDate = input<dayjs.Dayjs | undefined>(undefined);
    readonly taskName = input<string | undefined>(undefined);
    readonly numberOfNotExecutedTests = input<number | undefined>(undefined);
    /**
     * For programming exercises with individual due dates automatic feedbacks for tests marked as AFTER_DUE_DATE
     * are hidden until the last student can no longer submit. Students should be informed why some feedbacks seem
     * to be missing from the result.
     */
    readonly showMissingAutomaticFeedbackInformation = input(false);

    // These inputs may be omitted by callers: the component derives the effective value below (and can override it at
    // runtime), so read `resolvedExercise` / `exerciseType` / `scoreChartVisible` internally, never the raw inputs.
    readonly exercise = input<Exercise | undefined>(undefined);
    readonly showScoreChart = input(false);

    /** The exercise to show feedback for; defaults to the participation's exercise when not bound. */
    readonly resolvedExercise = computed<Exercise | undefined>(() => this.exercise() ?? this.participation()?.exercise);
    /**
     * The exercise type: the resolved exercise's type, or PROGRAMMING when only a programming participation is known.
     * There is no dedicated input — every caller previously passed `exercise.type`, so it is derived here instead.
     */
    readonly exerciseType = computed<ExerciseType | undefined>(
        () => this.resolvedExercise()?.type ?? (isProgrammingExerciseParticipation(this.participation()) ? ExerciseType.PROGRAMMING : undefined),
    );
    /** Whether the score chart is currently shown; seeded from the input, hidden at runtime once we know there is no chart data (see updateChart). */
    readonly scoreChartVisible = linkedSignal(() => this.showScoreChart());

    readonly isExamReviewPage = input(false);
    readonly isPrinting = input(false);

    // Icons
    faCircleNotch = faCircleNotch;
    faExclamationTriangle = faExclamationTriangle;
    readonly isLoading = signal(false);
    readonly loadingFailed = signal(false);
    readonly buildLogs = signal<BuildLogEntryArray | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    readonly isOnlyCompilationTested = signal<boolean>(undefined!);

    readonly commitHash = signal<string | undefined>(undefined);

    readonly chartData = signal<FeedbackChartData>({
        xScaleMax: 100,
        colors: [GraphColors.GREEN, GraphColors.RED],
        results: [],
    });
    private readonly chartColors = computed(() => this.chartData().colors);
    readonly scoreChartData = computed(() => stackedBarChart(this.chartData().results, this.chartColors()));
    readonly scoreChartConfig = computed<TumUiBarChartConfig>(() => ({
        horizontal: true,
        stacked: true,
        maxBarThickness: 25,
        xAxis: { max: this.chartData().xScaleMax, tickFormatter: (value) => this.xAxisFormatting(String(value)) },
        yAxis: { display: false },
        legend: { position: 'bottom' },
        tooltip: false,
    }));

    readonly badge = signal<Badge | undefined>(undefined);

    feedbackItemService!: FeedbackItemService; // set in ngOnInit() (selected based on exercise type)
    readonly feedbackItemNodes = signal<FeedbackNode[] | undefined>(undefined);

    /**
     * Load the result feedbacks if necessary and assign them to the component.
     * When a result has feedbacks assigned to it, no server call will be executed.
     */
    ngOnInit(): void {
        // Inputs are supplied via template bindings or, for the DialogService case, via `inputValues` (setInput),
        // so they are already populated here regardless of how the component was opened.
        this.isLoading.set(true);

        this.initializeExerciseInformation();

        this.feedbackItemService =
            this.exerciseType() === ExerciseType.PROGRAMMING ? this.injector.get(ProgrammingFeedbackItemService) : this.injector.get(FeedbackItemServiceImpl);
        this.initFeedbackInformation();

        this.commitHash.set(this.getCommitHash().slice(0, 11));

        this.isOnlyCompilationTested.set(
            isOnlyCompilationTested(
                this.result(),
                this.participation(),
                evaluateTemplateStatus(this.resolvedExercise(), this.result().submission?.participation, this.result(), false),
            ),
        );
    }

    /**
     * Sets up the information related to the exercise.
     */
    private initializeExerciseInformation() {
        // `exercise` and `exerciseType` are resolved reactively (see their computed signals above); here we only
        // derive the non-signal state that depends on them.
        const exercise = this.resolvedExercise();
        if (exercise) {
            this.course.set(getCourseFromExercise(exercise));
        }

        this.showTestDetails =
            exercise?.isAtLeastTutor || (this.exerciseType() === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise)?.showTestNamesToStudents) || false;
    }

    /**
     * Fetches additional information about feedbacks and build logs if required.
     */
    private initFeedbackInformation() {
        const result = this.result();
        const participation = this.participation();
        of(result.feedbacks)
            .pipe(
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    // don't query the server if feedback already exists
                    if (feedbacks?.length) {
                        // ensure connection to result, required for FeedbackItems in the next step
                        feedbacks.forEach((feedback) => (feedback.result = result));
                        return of(feedbacks);
                    } else {
                        return this.resultService.getFeedbackDetailsForResult(participation?.id, result).pipe(map((response) => response.body));
                    }
                }),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    if (feedbacks?.length) {
                        result.feedbacks = feedbacks!;

                        const filteredFeedback = this.feedbackService.filterFeedback(feedbacks, this.feedbackFilter());
                        checkSubsequentFeedbackInAssessment(filteredFeedback);
                        const feedbackItems = this.feedbackItemService.create(filteredFeedback, this.showTestDetails);
                        const exercise = this.resolvedExercise();
                        if (exercise) {
                            this.feedbackItemNodes.set(this.feedbackItemService.group(feedbackItems, exercise));
                            this.enrichFeedbackItemsWithCodeReferences(feedbackItems);
                        }
                        if (this.isExamReviewPage()) {
                            this.expandFeedbackItemGroups();
                        }
                    }

                    // prefer the potentially newer result.submission when available (so that buildFailed is up-to-date)
                    const submission = (result.submission ?? getLatestSubmission(participation)) as ProgrammingSubmission;
                    // If the submission is marked with buildFailed, fetch the build logs.
                    const buildFailed = submission?.buildFailed;

                    const participationId = participation.id;
                    if (
                        result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA &&
                        this.exerciseType() === ExerciseType.PROGRAMMING &&
                        buildFailed &&
                        participationId !== undefined
                    ) {
                        return this.fetchAndSetBuildLogs(participationId, result.id);
                    }

                    if (this.scoreChartVisible() && this.feedbackItemNodes() !== undefined) {
                        this.updateChart(this.feedbackItemNodes()!);
                    }

                    if (isStudentParticipation(participation)) {
                        this.badge.set(ResultService.evaluateBadge(participation, result));
                    }

                    return of(null);
                }),
                catchError(() => {
                    this.loadingFailed.set(true);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading.set(false);
            });
    }

    private enrichFeedbackItemsWithCodeReferences(feedbackItems: FeedbackItem[]): void {
        const participation = this.participation();
        const exerciseId = this.resolvedExercise()?.id;
        const participationId = participation.id;
        const commitHash = (this.result().submission as ProgrammingSubmission)?.commitHash;
        const referencedFilePaths = [...new Set(feedbackItems.flatMap((item) => (item.codeReference ? [item.codeReference.filePath] : [])))];
        if (this.exerciseType() !== ExerciseType.PROGRAMMING || exerciseId === undefined || participationId === undefined || !commitHash || referencedFilePaths.length === 0) {
            return;
        }

        this.programmingExerciseParticipationService
            .getSelectedParticipationRepositoryFilesAtCommit(exerciseId, participationId, commitHash, referencedFilePaths)
            .pipe(
                take(1),
                catchError(() => EMPTY),
            )
            .subscribe((fileContentByPath) => {
                if (!fileContentByPath) {
                    return;
                }
                feedbackItems.forEach((item) => {
                    const reference = item.codeReference;
                    const fileContent = reference && fileContentByPath.get(reference.filePath);
                    if (reference && fileContent) {
                        reference.lines = this.getReferencedLines(fileContent, reference.line, reference.lineEnd ?? reference.line);
                    }
                });
                this.feedbackItemNodes.update((nodes) => (nodes ? [...nodes] : nodes));
            });
    }

    private getReferencedLines(fileContent: string, lineStart: number, lineEnd: number): { line: number; code: string; referenced: boolean }[] {
        const lines = fileContent.split(/\r?\n/);
        const firstLine = Math.max(1, lineStart - CODE_REFERENCE_CONTEXT_LINES);
        const lastLine = Math.min(lines.length, lineEnd + CODE_REFERENCE_CONTEXT_LINES, firstLine + MAX_DISPLAYED_CODE_REFERENCE_LINES - 1);
        return lines.slice(firstLine - 1, lastLine).map((code, index) => {
            const line = firstLine + index;
            return { line, code, referenced: line >= lineStart && line <= lineEnd };
        });
    }

    /**
     * Fetches build logs for a participation
     * @param participationId The active participation
     * @param resultId The current result
     */
    private fetchAndSetBuildLogs = (participationId: number, resultId?: number) => {
        return this.buildLogService.getBuildLogs(participationId, resultId).pipe(
            tap((repoResult: BuildLogEntry[]) => {
                this.buildLogs.set(BuildLogEntryArray.fromBuildLogs(repoResult));
            }),
            catchError((error: HttpErrorResponse) => {
                /**
                 * The request returns 403 if the build was successful and therefore no build logs exist.
                 * If no submission is available, the client will attempt to fetch the build logs anyway.
                 * We catch the error here as it would prevent the displaying of feedback.
                 */
                if (error.status === 403) {
                    return of(null);
                }
                return throwError(() => error);
            }),
        );
    };

    private updateChart(feedbackItemNodes: FeedbackNode[]) {
        const exercise = this.resolvedExercise();
        if (!exercise || feedbackItemNodes.length === 0) {
            this.scoreChartVisible.set(false);
            return;
        }

        this.chartData.set(this.feedbackChartService.create(feedbackItemNodes, exercise));
    }

    getCommitHash(): string {
        return (this.result()?.submission as ProgrammingSubmission)?.commitHash ?? 'n.a.';
    }

    private expandFeedbackItemGroups() {
        this.feedbackItemNodes()?.forEach((feedbackNode) => {
            if (isFeedbackGroup(feedbackNode)) {
                feedbackNode.open = true;
            }
        });
    }
}
