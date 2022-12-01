import { Component, Injector, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { createCommitUrl, isProgrammingExerciseParticipation } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { round, roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Color, LegendPosition, ScaleType } from '@swimlane/ngx-charts';
import { faCircleNotch, faExclamationTriangle, faXmark } from '@fortawesome/free-solid-svg-icons';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/statistics-graph.utils';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { FeedbackItemService, FeedbackItemServiceImpl } from 'app/exercises/shared/feedback/feedback-item-service';
import { ProgrammingExerciseFeedbackItemService } from 'app/exercises/shared/feedback/programming-exercise-feedback-item.service';
import { FeedbackService } from 'app/exercises/shared/feedback/feedback-service';

export enum FeedbackItemType {
    Issue,
    Test,
    Feedback,
    Policy,
    Subsequent,
}

export class FeedbackItem {
    type: FeedbackItemType;
    category: string;
    previewText?: string; // used for long texts with line breaks
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    credits?: number;
    actualCredits?: number;
}

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
    styleUrls: ['./result-detail.scss'],
    providers: [ProgrammingExerciseFeedbackItemService, FeedbackItemServiceImpl],
})
export class ResultDetailComponent implements OnInit {
    readonly BuildLogType = BuildLogType;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly xAxisFormatting = axisTickFormattingWithPercentageSign;

    @Input() exercise?: Exercise;
    @Input() result: Result;
    // Specify the feedback.text values that should be shown, all other values will not be visible.
    @Input() feedbackFilter: string[];
    @Input() showTestDetails = false;
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
    @Input() latestIndividualDueDate?: dayjs.Dayjs;
    @Input() taskName?: string;
    @Input() numberOfNotExecutedTests?: number;

    // Icons
    faXmark = faXmark;
    faCircleNotch = faCircleNotch;
    faExclamationTriangle = faExclamationTriangle;

    isLoading = false;
    loadingFailed = false;
    feedbackList: FeedbackItem[];
    filteredFeedbackList: FeedbackItem[];
    backupFilteredFeedbackList: FeedbackItem[];
    buildLogs: BuildLogEntryArray;
    course?: Course;

    showScoreChartTooltip = false;

    commitHashURLTemplate?: string;
    commitHash?: string;
    commitUrl?: string;

    testCaseCount: number;
    passedTestCaseCount: number;
    scaFeedbackCount: number;
    manualFeedbackCount: number;

    ngxData: NgxChartsMultiSeriesDataEntry[] = [];
    labels: string[];
    ngxColors = {
        name: 'Feedback Detail',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED],
    } as Color;
    xScaleMax = 100;
    legendPosition = LegendPosition.Below;
    showOnlyPositiveFeedback = false;
    showOnlyNegativeFeedback = false;

    numberOfAggregatedTestCases = 0;

    feedbackItemService: FeedbackItemService;

    constructor(
        public activeModal: NgbActiveModal,
        private resultService: ResultService,
        private buildLogService: BuildLogService,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private feedbackService: FeedbackService,
        private injector: Injector,
    ) {
        const pointsLabel = translateService.instant('artemisApp.result.chart.points');
        const deductionsLabel = translateService.instant('artemisApp.result.chart.deductions');
        this.labels = [pointsLabel, deductionsLabel];
    }

    /**
     * Load the result feedbacks if necessary and assign them to the component.
     * When a result has feedbacks assigned to it, no server call will be executed.
     *
     */
    ngOnInit(): void {
        this.isLoading = true;

        this.initializeExerciseInformation();

        this.feedbackItemService =
            this.exerciseType === ExerciseType.PROGRAMMING ? this.injector.get(ProgrammingExerciseFeedbackItemService) : this.injector.get(FeedbackItemServiceImpl);
        this.fetchAdditionalInformation();

        this.commitHash = this.getCommitHash().slice(0, 11);

        // Get active profiles, to distinguish between Bitbucket and GitLab for the commit link of the result
        this.profileService.getProfileInfo().subscribe((info: ProfileInfo) => {
            this.commitHashURLTemplate = info?.commitHashURLTemplate;
            this.commitUrl = this.getCommitUrl();
        });
    }

    /**
     * Sets up the information related to the exercise.
     * @private
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
    }

    /**
     * Fetches additional information about feedbacks and build logs if required.
     * @private
     */
    private fetchAdditionalInformation() {
        of(this.result.feedbacks)
            .pipe(
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    // don't query the server if feedback already exists
                    if (feedbacks?.length) {
                        return of(feedbacks);
                    } else {
                        return this.feedbackService.getDetailsForResult(this.result.participation!.id!, this.result.id!);
                    }
                }),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    /*
                     * If we have feedback, filter it if needed, distinguish between test case and static code analysis
                     * feedback and assign the lists to the component
                     */
                    if (feedbacks && feedbacks.length) {
                        this.result.feedbacks = feedbacks!;
                        const filteredFeedback = this.feedbackService.filterFeedback(feedbacks, this.feedbackFilter);
                        checkSubsequentFeedbackInAssessment(filteredFeedback);

                        this.feedbackList = this.feedbackItemService.createFeedbackItems(filteredFeedback, this.showTestDetails);
                        this.filteredFeedbackList = this.feedbackItemService.filterFeedbackItems(this.feedbackList, this.showTestDetails);
                        this.numberOfAggregatedTestCases = this.feedbackItemService.getPositiveTestCasesWithoutDetailText(this.feedbackList).length;
                        this.backupFilteredFeedbackList = this.filteredFeedbackList;

                        this.countFeedbacks();

                        if (this.showScoreChart) {
                            this.updateChart(this.feedbackList);
                        }
                    }

                    // If we don't receive a submission or the submission is marked with buildFailed, fetch the build logs.
                    if (
                        this.exerciseType === ExerciseType.PROGRAMMING &&
                        this.result.participation &&
                        (!this.result.submission || (this.result.submission as ProgrammingSubmission).buildFailed)
                    ) {
                        return this.fetchAndSetBuildLogs(this.result.participation.id!, this.result.id);
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

    /**
     * Calculates and updates the values of the score chart
     * @param feedbackList The list of feedback items.
     * @private
     */
    private updateChart(feedbackList: FeedbackItem[]) {
        if (!this.exercise || feedbackList.length === 0) {
            this.showScoreChart = false;
            return;
        }

        const sumCredits = (sum: number, feedbackItem: FeedbackItem) => sum + (feedbackItem.credits || 0);
        const sumActualCredits = (sum: number, feedbackItem: FeedbackItem) => sum + (feedbackItem.actualCredits || 0);

        let testCaseCredits = feedbackList.filter((item) => item.type === FeedbackItemType.Test).reduce(sumCredits, 0);
        const positiveCredits = feedbackList.filter((item) => item.type !== FeedbackItemType.Test && item.credits && item.credits > 0).reduce(sumCredits, 0);

        let codeIssueCredits = -feedbackList.filter((item) => item.type === FeedbackItemType.Issue).reduce(sumActualCredits, 0);
        const codeIssuePenalties = -feedbackList.filter((item) => item.type === FeedbackItemType.Issue).reduce(sumCredits, 0);
        const negativeCredits = -feedbackList.filter((item) => item.type !== FeedbackItemType.Issue && item.credits && item.credits < 0).reduce(sumCredits, 0);

        // cap test points
        const maxPoints = this.exercise.maxPoints!;
        const maxPointsWithBonus = maxPoints + (this.exercise.bonusPoints || 0);

        if (testCaseCredits > maxPointsWithBonus) {
            testCaseCredits = maxPointsWithBonus;
        }

        // cap sca penalty points
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            if (programmingExercise.staticCodeAnalysisEnabled && programmingExercise.maxStaticCodeAnalysisPenalty != undefined) {
                const maxPenaltyCredits = (maxPoints * programmingExercise.maxStaticCodeAnalysisPenalty) / 100;
                codeIssueCredits = Math.min(codeIssueCredits, maxPenaltyCredits);
            }
        }

        const course = getCourseFromExercise(this.exercise);

        const appliedNegativePoints = roundValueSpecifiedByCourseSettings(codeIssueCredits + negativeCredits, course);
        const receivedNegativePoints = roundValueSpecifiedByCourseSettings(codeIssuePenalties + negativeCredits, course);
        const positivePoints = roundValueSpecifiedByCourseSettings(testCaseCredits + positiveCredits, course);

        if (appliedNegativePoints !== receivedNegativePoints) {
            this.showScoreChartTooltip = true;
        }
        this.setValues(positivePoints, appliedNegativePoints, receivedNegativePoints, maxPoints, maxPointsWithBonus);
    }

    getCommitHash(): string {
        return (this.result?.submission as ProgrammingSubmission)?.commitHash ?? 'n.a.';
    }

    getCommitUrl(): string | undefined {
        const projectKey = (this.exercise as ProgrammingExercise)?.projectKey;
        const programmingSubmission = this.result.submission as ProgrammingSubmission;
        return createCommitUrl(this.commitHashURLTemplate, projectKey, this.result.participation, programmingSubmission);
    }

    /**
     * Updates the datasets of the charts with the correct values and colors.
     * @param receivedPositive Sum of positive credits of the score
     * @param appliedNegative Sum of applied negative credits
     * @param receivedNegative Sum of received negative credits
     * @param maxScore The relevant maximal points of the exercise
     * @param maxScoreWithBonus The actual received points + optional bonus points
     */
    setValues(receivedPositive: number, appliedNegative: number, receivedNegative: number, maxScore: number, maxScoreWithBonus: number): void {
        this.ngxData = [
            {
                name: this.translateService.instant('artemisApp.exercise.score'),
                series: [
                    { name: this.labels[0], value: 0, isPositive: true },
                    { name: this.labels[1], value: 0, isPositive: false },
                ],
            },
        ];
        let appliedPositive = receivedPositive;

        // cap to min and max values while maintaining correct negative points
        if (appliedPositive - appliedNegative > maxScoreWithBonus) {
            appliedPositive = maxScoreWithBonus;
            appliedNegative = 0;
        } else if (appliedPositive > maxScoreWithBonus) {
            appliedNegative -= appliedPositive - maxScoreWithBonus;
            appliedPositive = maxScoreWithBonus;
        } else if (appliedPositive - appliedNegative < 0) {
            appliedNegative = appliedPositive;
        }
        const score = this.roundToDecimals(((appliedPositive - appliedNegative) / maxScore) * 100, 2);
        this.xScaleMax = Math.max(this.xScaleMax, score);
        this.ngxData[0].series[0].value = score;
        this.ngxData[0].series[0].name +=
            ': ' + this.roundToDecimals(appliedPositive, 1) + (appliedPositive !== receivedPositive ? ` of ${this.roundToDecimals(receivedPositive, 1)}` : '');
        this.ngxData[0].series[1].value = this.roundToDecimals((appliedNegative / maxScore) * 100, 2);
        this.ngxData[0].series[1].name +=
            ': ' + this.roundToDecimals(appliedNegative, 1) + (appliedNegative !== receivedNegative ? ` of ${this.roundToDecimals(receivedNegative, 1)}` : '');
        this.ngxData = [...this.ngxData];
    }

    private roundToDecimals(i: number, n: number) {
        const f = 10 ** n;
        return round(i, f);
    }

    /**
     * Handles the event if the user clicks on a part of the chart.
     * If the user clicks on a legend entry, the corresponding bar disappears.
     * If the user clicks on a bar, the feedback items get filtered accordingly:
     * Click on the green bar -> Only the positive feedback is shown
     * Click in the red bar -> Only the feedback concerning deductions is shown
     * In order to prevent confusion, additionally a disclaimer is shown that states that the feedback items are currently filtered.
     * @param event the information that is delegated by the chart framework. It is dependent on the spot
     * the user clicks
     */
    onSelect(event: any): void {
        if (!event.series) {
            const name = event as string;
            this.ngxData[0].series.forEach((points: any, index: number) => {
                if (points.name === name) {
                    const color = this.ngxColors.domain[index];
                    // if the bar is not transparent yet, make it transparent. Else, reset the normal color
                    if (color !== 'rgba(255,255,255,0)') {
                        this.ngxColors.domain[index] = 'rgba(255,255,255,0)';
                    } else {
                        this.ngxColors.domain[index] = index === 0 ? GraphColors.GREEN : GraphColors.RED;
                    }

                    // update is necessary for the colors to change
                    this.ngxData = [...this.ngxData];
                }
            });
        } else {
            this.filterFeedbackListByChart(event.isPositive);
        }
    }

    /**
     * Method that handles the filter reset applied by the chart
     */
    resetChartFilter() {
        this.showOnlyNegativeFeedback = false;
        this.showOnlyPositiveFeedback = false;
        this.filteredFeedbackList = this.backupFilteredFeedbackList;
        this.countFeedbacks();
    }

    /**
     * Auxiliary method that handles the filtering of the feedback items if a chart bar is clicked
     * @param isPositive the indicator whether the bar representing the positive (point achieving) or the negative (point deducting) feedback is clicked
     * @private
     */
    private filterFeedbackListByChart(isPositive: boolean) {
        let filterPredicate;
        if (isPositive) {
            this.showOnlyPositiveFeedback = !this.showOnlyPositiveFeedback;
            filterPredicate = (feedback: FeedbackItem) => feedback.positive === true;
            this.showOnlyNegativeFeedback = false;
        } else {
            this.showOnlyNegativeFeedback = !this.showOnlyNegativeFeedback;
            // by the second predicate we filter all feedback items that do not deduct any points
            filterPredicate = (feedback: FeedbackItem) => feedback.positive === false && feedback.actualCredits;
            this.showOnlyPositiveFeedback = false;
        }
        // we reset the item list in order to make sure that maximal one feedback type is filtered at any time by the chart
        this.filteredFeedbackList = this.backupFilteredFeedbackList;
        if (this.showOnlyNegativeFeedback || this.showOnlyPositiveFeedback) {
            this.filteredFeedbackList = this.filteredFeedbackList.filter(filterPredicate);
        }

        this.countFeedbacks();
    }

    private countFeedbacks() {
        const testCaseList = this.filteredFeedbackList.filter((feedback) => feedback.type === FeedbackItemType.Test);
        if (this.numberOfAggregatedTestCases && (this.showOnlyPositiveFeedback || !this.showOnlyNegativeFeedback)) {
            // The positive test feedbacks were aggregated to one, so we have to add the number but subtract one, since the aggregated test case is in the testCaseList
            this.testCaseCount = testCaseList.length + this.numberOfAggregatedTestCases - 1;
            this.passedTestCaseCount = testCaseList.filter((feedback) => feedback.positive).length + this.numberOfAggregatedTestCases - 1;
        } else {
            this.testCaseCount = testCaseList.length;
            this.passedTestCaseCount = testCaseList.filter((feedback) => feedback.positive).length;
        }

        this.scaFeedbackCount = this.filteredFeedbackList.filter((feedback) => feedback.type === FeedbackItemType.Issue).length;
        this.manualFeedbackCount = this.filteredFeedbackList.filter(
            (feedback) => feedback.type === FeedbackItemType.Feedback || feedback.type === FeedbackItemType.Subsequent,
        ).length;
    }
}
