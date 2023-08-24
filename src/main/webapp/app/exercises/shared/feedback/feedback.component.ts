import { Component, Injector, Input, OnInit, Optional } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { Badge, ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { createCommitUrl, isProgrammingExerciseParticipation } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LegendPosition, ScaleType } from '@swimlane/ngx-charts';
import { faCircleNotch, faExclamationTriangle, faXmark } from '@fortawesome/free-solid-svg-icons';
import { GraphColors } from 'app/entities/statistics.model';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/statistics-graph.utils';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { FeedbackItemService, FeedbackItemServiceImpl } from 'app/exercises/shared/feedback/item/feedback-item-service';
import { ProgrammingFeedbackItemService } from 'app/exercises/shared/feedback/item/programming-feedback-item.service';
import { FeedbackService } from 'app/exercises/shared/feedback/feedback.service';
import { evaluateTemplateStatus, isOnlyCompilationTested, resultIsPreliminary } from '../result/result.utils';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';
import { ChartData } from 'app/exercises/shared/feedback/chart/feedback-chart-data';
import { FeedbackChartService } from 'app/exercises/shared/feedback/chart/feedback-chart.service';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './feedback.component.html',
    styleUrls: ['./feedback.scss'],
})
export class FeedbackComponent implements OnInit {
    readonly BuildLogType = BuildLogType;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly resultIsPreliminary = resultIsPreliminary;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly xAxisFormatting = axisTickFormattingWithPercentageSign;

    @Input() exercise?: Exercise;
    @Input() result: Result;
    // Specify the feedback.text values that should be shown, all other values will not be visible.
    @Input() feedbackFilter: string[];
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

    // Icons
    faXmark = faXmark;
    faCircleNotch = faCircleNotch;
    faExclamationTriangle = faExclamationTriangle;

    private showTestDetails = false;
    isLoading = false;
    loadingFailed = false;
    buildLogs: BuildLogEntryArray;
    course?: Course;
    isOnlyCompilationTested: boolean;

    commitHashURLTemplate?: string;
    commitHash?: string;
    commitUrl?: string;

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

    constructor(
        private resultService: ResultService,
        private buildLogService: BuildLogService,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private feedbackService: FeedbackService,
        private feedbackChartService: FeedbackChartService,
        private injector: Injector,
        @Optional()
        public activeModal?: NgbActiveModal,
    ) {
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

        this.isOnlyCompilationTested = isOnlyCompilationTested(this.result, evaluateTemplateStatus(this.exercise, this.result.participation, this.result, false));

        // Get active profiles, to distinguish between Bitbucket and GitLab for the commit link of the result
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.commitHashURLTemplate = profileInfo?.commitHashURLTemplate;
            this.commitUrl = this.getCommitUrl();
        });
    }

    /**
     * Sets up the information related to the exercise.
     */
    private initializeExerciseInformation() {
        this.exercise ??= this.result.participation?.exercise;
        if (this.exercise) {
            this.course = getCourseFromExercise(this.exercise);
        }

        if (!this.exerciseType && this.exercise?.type) {
            this.exerciseType = this.exercise.type;
        }

        // In case the exerciseType is not set, we try to set it back if the participation is from a programming exercise
        if (!this.exerciseType && isProgrammingExerciseParticipation(this.result?.participation)) {
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
                        return this.resultService.getFeedbackDetailsForResult(this.result.participation!.id!, this.result).pipe(map((response) => response.body));
                    }
                }),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    if (feedbacks?.length) {
                        this.result.feedbacks = feedbacks!;

                        const filteredFeedback = this.feedbackService.filterFeedback(feedbacks, this.feedbackFilter);
                        checkSubsequentFeedbackInAssessment(filteredFeedback);

                        const feedbackItems = this.feedbackItemService.create(filteredFeedback, this.showTestDetails);
                        this.feedbackItemNodes = this.feedbackItemService.group(feedbackItems, this.exercise!);
                    }

                    // If we don't receive a submission or the submission is marked with buildFailed, fetch the build logs.
                    if (
                        this.exerciseType === ExerciseType.PROGRAMMING &&
                        this.result.participation &&
                        (!this.result.submission || (this.result.submission as ProgrammingSubmission).buildFailed)
                    ) {
                        return this.fetchAndSetBuildLogs(this.result.participation.id!, this.result.id);
                    }

                    if (this.showScoreChart) {
                        this.updateChart(this.feedbackItemNodes);
                    }

                    this.badge = ResultService.evaluateBadge(this.result.participation!, this.result);

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

    getCommitUrl(): string | undefined {
        const projectKey = (this.exercise as ProgrammingExercise)?.projectKey;
        const programmingSubmission = this.result.submission as ProgrammingSubmission;
        return createCommitUrl(this.commitHashURLTemplate, projectKey, this.result.participation, programmingSubmission);
    }
}
