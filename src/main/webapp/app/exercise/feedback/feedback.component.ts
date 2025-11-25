import { Component, Injector, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/buildagent/shared/entities/build-log.model';
import { Feedback, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BuildLogService } from 'app/programming/shared/services/build-log.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { isProgrammingExerciseParticipation } from 'app/programming/shared/utils/programming-exercise.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { BarChartModule, LegendPosition, ScaleType } from '@swimlane/ngx-charts';
import { faCircleNotch, faExclamationTriangle, faXmark } from '@fortawesome/free-solid-svg-icons';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/util/statistics-graph.utils';
import { Course } from 'app/core/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { FeedbackItemService, FeedbackItemServiceImpl } from 'app/exercise/feedback/item/feedback-item-service';
import { ProgrammingFeedbackItemService } from 'app/exercise/feedback/item/programming-feedback-item.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { evaluateTemplateStatus, isOnlyCompilationTested, isStudentParticipation, resultIsPreliminary } from '../result/result.utils';
import { FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { ChartData } from 'app/exercise/feedback/chart/feedback-chart-data';
import { FeedbackChartService } from 'app/exercise/feedback/chart/feedback-chart.service';
import { isFeedbackGroup } from 'app/exercise/feedback/group/feedback-group';
import { cloneDeep } from 'lodash-es';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, NgTemplateOutlet, UpperCasePipe } from '@angular/common';
import { FeedbackNodeComponent } from './node/feedback-node.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { Participation, getLatestSubmission } from 'app/exercise/shared/entities/participation/participation.model';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './feedback.component.html',
    styleUrls: ['./feedback.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        NgClass,
        NgbTooltip,
        BarChartModule,
        NgTemplateOutlet,
        FeedbackNodeComponent,
        UpperCasePipe,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class FeedbackComponent implements OnInit, OnChanges {
    private resultService = inject(ResultService);
    private buildLogService = inject(BuildLogService);
    private translateService = inject(TranslateService);
    private feedbackService = inject(FeedbackService);
    private feedbackChartService = inject(FeedbackChartService);
    private injector = inject(Injector);
    activeModal? = inject(NgbActiveModal, { optional: true });

    readonly BuildLogType = BuildLogType;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly resultIsPreliminary = resultIsPreliminary;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly xAxisFormatting = axisTickFormattingWithPercentageSign;

    private showTestDetails = false;

    @Input() exercise?: Exercise;
    @Input() result: Result;
    @Input() participation: Participation;

    /**
     * Specify the feedback.testCase.id values that should be shown, all other values will not be visible.
     * Used to show only feedback related to a specific task.
     */
    @Input() feedbackFilter: number[];
    @Input() showScoreChart = false;
    @Input() exerciseType: ExerciseType;
    /**
     * Translate key for an HTML message that is displayed at the top of the result details, if defined.
     */
    @Input() messageKey?: string = undefined;
    /**
     * For programming exercises with individual due dates automatic feedbacks
     * for tests marked as AFTER_DUE_DATE are hidden until the last student can
     * no longer submit.
     * Students should be informed why some feedbacks seem to be missing from
     * the result.
     */
    @Input() showMissingAutomaticFeedbackInformation = false;
    @Input() latestDueDate?: dayjs.Dayjs;
    @Input() taskName?: string;
    @Input() numberOfNotExecutedTests?: number;
    @Input() isExamReviewPage = false;
    @Input() isPrinting = false;

    // Icons
    faXmark = faXmark;
    faCircleNotch = faCircleNotch;
    faExclamationTriangle = faExclamationTriangle;
    isLoading = false;
    loadingFailed = false;
    buildLogs: BuildLogEntryArray;
    course?: Course;
    isOnlyCompilationTested: boolean;

    commitHash?: string;

    chartData: ChartData = {
        xScaleMax: 100,
        scheme: {
            name: 'Feedback Detail',
            selectable: true,
            group: ScaleType.Ordinal,
            domain: [GraphColors.GREEN, GraphColors.RED],
        },
        results: [],
    };
    // Static chart settings
    labels: string[];
    legendPosition = LegendPosition.Below;

    badge: Badge;

    feedbackItemService: FeedbackItemService;
    feedbackItemNodes: FeedbackNode[];
    /**
     * Used to reset the feedbackItemNodes to the state before printing if {@link isPrinting} changes
     * from true to false
     */
    private feedbackItemNodesBeforePrinting: FeedbackNode[];

    constructor() {
        const translateService = this.translateService;

        const pointsLabel = translateService.instant('artemisApp.result.chart.points');
        const deductionsLabel = translateService.instant('artemisApp.result.chart.deductions');
        this.labels = [pointsLabel, deductionsLabel];
    }

    /**
     * Load the result feedbacks if necessary and assign them to the component.
     * When a result has feedbacks assigned to it, no server call will be executed.
     */
    ngOnInit(): void {
        this.isLoading = true;

        this.initializeExerciseInformation();

        this.feedbackItemService = this.exerciseType === ExerciseType.PROGRAMMING ? this.injector.get(ProgrammingFeedbackItemService) : this.injector.get(FeedbackItemServiceImpl);
        this.initFeedbackInformation();

        this.commitHash = this.getCommitHash().slice(0, 11);

        this.isOnlyCompilationTested = isOnlyCompilationTested(
            this.result,
            this.participation,
            evaluateTemplateStatus(this.exercise, this.result.submission?.participation, this.result, false),
        );
    }

    /**
     * Expand the feedback items groups while the exam summary is printed and
     * collapse them again (if collapsed before) when the printing is done
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.isPrinting) {
            if (changes.isPrinting.currentValue) {
                this.feedbackItemNodesBeforePrinting = cloneDeep(this.feedbackItemNodes);
                this.expandFeedbackItemGroups();
            } else {
                this.feedbackItemNodes = this.feedbackItemNodesBeforePrinting;
            }
        }
    }

    /**
     * Sets up the information related to the exercise.
     */
    private initializeExerciseInformation() {
        this.exercise ??= this.participation?.exercise;
        if (this.exercise) {
            this.course = getCourseFromExercise(this.exercise);
        }

        if (!this.exerciseType && this.exercise?.type) {
            this.exerciseType = this.exercise.type;
        }

        // In case the exerciseType is not set, we try to set it back if the participation is from a programming exercise
        if (!this.exerciseType && isProgrammingExerciseParticipation(this.participation)) {
            this.exerciseType = ExerciseType.PROGRAMMING;
        }

        this.showTestDetails =
            this.exercise?.isAtLeastTutor || (this.exerciseType === ExerciseType.PROGRAMMING && (this.exercise as ProgrammingExercise)?.showTestNamesToStudents) || false;
    }

    /**
     * Fetches additional information about feedbacks and build logs if required.
     */
    private initFeedbackInformation() {
        of(this.result.feedbacks)
            .pipe(
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    // don't query the server if feedback already exists
                    if (feedbacks?.length) {
                        // ensure connection to result, required for FeedbackItems in the next step
                        feedbacks.forEach((feedback) => (feedback.result = this.result));
                        return of(feedbacks);
                    } else {
                        return this.resultService.getFeedbackDetailsForResult(this.participation?.id, this.result).pipe(map((response) => response.body));
                    }
                }),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    if (feedbacks?.length) {
                        this.result.feedbacks = feedbacks!;

                        const filteredFeedback = this.feedbackService.filterFeedback(feedbacks, this.feedbackFilter);
                        checkSubsequentFeedbackInAssessment(filteredFeedback);
                        const feedbackItems = this.feedbackItemService.create(filteredFeedback, this.showTestDetails);
                        this.feedbackItemNodes = this.feedbackItemService.group(feedbackItems, this.exercise!);
                        if (this.isExamReviewPage) {
                            this.expandFeedbackItemGroups();
                        }
                    }

                    // prefer the potentially newer result.submission when available (so that buildFailed is up-to-date)
                    const submission = (this.result.submission ?? getLatestSubmission(this.participation)) as ProgrammingSubmission;
                    // If the submission is marked with buildFailed, fetch the build logs.
                    const buildFailed = submission?.buildFailed;

                    if (this.result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA && this.exerciseType === ExerciseType.PROGRAMMING && buildFailed) {
                        return this.fetchAndSetBuildLogs(this.participation.id!, this.result.id);
                    }

                    if (this.showScoreChart) {
                        this.updateChart(this.feedbackItemNodes);
                    }

                    if (isStudentParticipation(this.participation)) {
                        this.badge = ResultService.evaluateBadge(this.participation, this.result);
                    }

                    return of(null);
                }),
                catchError(() => {
                    this.loadingFailed = true;
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
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
                this.buildLogs = BuildLogEntryArray.fromBuildLogs(repoResult);
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
        if (!this.exercise || feedbackItemNodes.length === 0) {
            this.showScoreChart = false;
            return;
        }

        this.chartData = this.feedbackChartService.create(feedbackItemNodes, this.exercise!);
    }

    getCommitHash(): string {
        return (this.result?.submission as ProgrammingSubmission)?.commitHash ?? 'n.a.';
    }

    private expandFeedbackItemGroups() {
        this.feedbackItemNodes.forEach((feedbackNode) => {
            if (isFeedbackGroup(feedbackNode)) {
                feedbackNode.open = true;
            }
        });
    }
}
