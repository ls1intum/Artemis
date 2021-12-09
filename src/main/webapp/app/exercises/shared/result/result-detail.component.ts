import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { getExercise } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import {
    createCommitUrl,
    isProgrammingExerciseParticipation,
    isProgrammingExerciseStudentParticipation,
    isResultPreliminary,
} from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { round, roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Color, LegendPosition, ScaleType } from '@swimlane/ngx-charts';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

export enum FeedbackItemType {
    Issue,
    Test,
    Feedback,
    Policy,
}

export class FeedbackItem {
    type: FeedbackItemType;
    category: string;
    previewText?: string; // used for long texts with line breaks
    title?: string; // this is typically feedback.text
    text?: string; // this is typically feedback.detailText
    positive?: boolean;
    credits?: number;
    appliedCredits?: number;
}

export const feedbackPreviewCharacterLimit = 300;

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
    styleUrls: ['./result-detail.scss'],
})
export class ResultDetailComponent implements OnInit {
    readonly BuildLogType = BuildLogType;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly roundScoreSpecifiedByCourseSettings = roundScoreSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

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

    isLoading = false;
    loadingFailed = false;
    feedbackList: FeedbackItem[];
    filteredFeedbackList: FeedbackItem[];
    buildLogs: BuildLogEntryArray;

    showScoreChartTooltip = false;

    commitHashURLTemplate?: string;
    commitHash?: string;
    commitUrl?: string;

    ngxData: any[] = [];
    labels: string[];
    ngxColors = {
        name: 'Feedback Detail',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#28a745', '#dc3545'], // colors: green, red
    } as Color;
    xScaleMax = 100;
    legendPosition = LegendPosition.Below;

    get exercise(): Exercise | undefined {
        if (this.result.participation) {
            return getExercise(this.result.participation);
        }
    }

    // Icons
    faCircleNotch = faCircleNotch;

    constructor(
        public activeModal: NgbActiveModal,
        private resultService: ResultService,
        private buildLogService: BuildLogService,
        translateService: TranslateService,
        private profileService: ProfileService,
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
        of(this.result.feedbacks)
            .pipe(
                // If the result already has feedbacks assigned to it, don't query the server.
                switchMap((feedbacks: Feedback[] | undefined | null) =>
                    feedbacks && feedbacks.length ? of(feedbacks) : this.getFeedbackDetailsForResult(this.result.participation!.id!, this.result.id!),
                ),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    // In case the exerciseType is not set, we try to set it back if the participation is from a programming exercise
                    if (!this.exerciseType && isProgrammingExerciseParticipation(this.result?.participation)) {
                        this.exerciseType = ExerciseType.PROGRAMMING;
                    }

                    /*
                     * If we have feedback, filter it if needed, distinguish between test case and static code analysis
                     * feedback and assign the lists to the component
                     */
                    if (feedbacks && feedbacks.length) {
                        this.result.feedbacks = feedbacks!;
                        const filteredFeedback = this.filterFeedback(feedbacks);
                        this.feedbackList = this.createFeedbackItems(filteredFeedback);
                        this.filteredFeedbackList = this.filterFeedbackItems(this.feedbackList);
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

        this.commitHash = this.getCommitHash().substr(0, 11);

        // Get active profiles, to distinguish between Bitbucket and GitLab for the commit link of the result
        this.profileService.getProfileInfo().subscribe((info: ProfileInfo) => {
            this.commitHashURLTemplate = info?.commitHashURLTemplate;
            this.commitUrl = this.getCommitUrl();
        });
    }

    /**
     * Loads the missing feedback details
     * @param participationId the current participation
     * @param resultId the current result
     * @private
     */
    private getFeedbackDetailsForResult(participationId: number, resultId: number) {
        return this.resultService.getFeedbackDetailsForResult(participationId, resultId).pipe(map(({ body: feedbackList }) => feedbackList!));
    }

    /**
     * Filters the feedback based on the filter input
     * @param feedbackList The full list of feedback
     */
    private filterFeedback = (feedbackList: Feedback[]) => {
        if (!this.feedbackFilter) {
            return [...feedbackList];
        } else {
            return this.feedbackFilter
                .map((filterText) => {
                    return feedbackList.find(({ text }) => text === filterText);
                })
                .filter(Boolean) as Feedback[];
        }
    };

    /**
     * computes the feedback preview for feedback texts with multiple lines or feedback that is longer than <feedbackPreviewCharacterLimit> characters
     * @param text the feedback.detail Text
     * @return the preview text (one line of text with at most <feedbackPreviewCharacterLimit> characters)
     */
    private static computeFeedbackPreviewText(text: string | undefined): string | undefined {
        if (text) {
            if (text.includes('\n')) {
                // if there are multiple lines, only use the first one
                const firstLine = text.substr(0, text.indexOf('\n'));
                if (firstLine.length > feedbackPreviewCharacterLimit) {
                    return firstLine.substr(0, feedbackPreviewCharacterLimit);
                } else {
                    return firstLine;
                }
            } else if (text.length > feedbackPreviewCharacterLimit) {
                return text.substr(0, feedbackPreviewCharacterLimit);
            }
        }
        // for all other cases
        return undefined; // this means the previewText is not used
    }

    /**
     * Creates a feedback item with a category, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @private
     */
    private createFeedbackItems(feedbacks: Feedback[]): FeedbackItem[] {
        if (this.exerciseType === ExerciseType.PROGRAMMING) {
            return feedbacks.map((feedback) => {
                const previewText = ResultDetailComponent.computeFeedbackPreviewText(feedback.detailText);
                if (Feedback.isSubmissionPolicyFeedback(feedback)) {
                    const submissionPolicyTitle = feedback.text!.substring(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER.length);
                    return {
                        type: FeedbackItemType.Policy,
                        category: 'Submission Policy',
                        title: submissionPolicyTitle,
                        text: feedback.detailText,
                        previewText,
                        positive: false,
                        credits: feedback.credits,
                        appliedCredits: feedback.credits,
                    };
                } else if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                    const scaCategory = feedback.text!.substring(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
                    const scaIssue = StaticCodeAnalysisIssue.fromFeedback(feedback);
                    return {
                        type: FeedbackItemType.Issue,
                        category: 'Code Issue',
                        title: `${scaCategory} Issue in file ${this.getIssueLocation(scaIssue)}`.trim(),
                        text: this.showTestDetails ? `${scaIssue.rule}: ${scaIssue.message}` : scaIssue.message,
                        previewText,
                        positive: false,
                        credits: scaIssue.penalty ? -scaIssue.penalty : feedback.credits,
                        appliedCredits: feedback.credits,
                    };
                } else if (feedback.type === FeedbackType.AUTOMATIC) {
                    return {
                        type: FeedbackItemType.Test,
                        category: this.showTestDetails ? 'Test Case' : 'Feedback',
                        title: !this.showTestDetails
                            ? undefined
                            : feedback.positive === undefined
                            ? `No result information for ${feedback.text}`
                            : `Test ${feedback.text} ${feedback.positive ? 'passed' : 'failed'}`,
                        text: feedback.detailText,
                        previewText,
                        positive: feedback.positive,
                        credits: feedback.credits,
                    };
                } else if ((feedback.type === FeedbackType.MANUAL || feedback.type === FeedbackType.MANUAL_UNREFERENCED) && feedback.gradingInstruction) {
                    return {
                        type: FeedbackItemType.Feedback,
                        category: this.showTestDetails ? 'Tutor' : 'Feedback',
                        title: feedback.text,
                        text: feedback.detailText ? feedback.gradingInstruction.feedback + '\n' + feedback.detailText : feedback.gradingInstruction.feedback,
                        previewText,
                        positive: feedback.positive,
                        credits: feedback.credits,
                    };
                } else {
                    return {
                        type: FeedbackItemType.Feedback,
                        category: this.showTestDetails ? 'Tutor' : 'Feedback',
                        title: feedback.text,
                        text: feedback.detailText,
                        previewText,
                        positive: feedback.positive,
                        credits: feedback.credits,
                    };
                }
            });
        } else {
            return feedbacks.map((feedback) => ({
                type: FeedbackItemType.Feedback,
                category: 'Feedback',
                title: feedback.text,
                text: feedback.detailText,
                previewText: ResultDetailComponent.computeFeedbackPreviewText(feedback.detailText),
                positive: feedback.positive,
                credits: feedback.credits,
            }));
        }
    }

    /**
     * Builds the location string for a static code analysis issue
     * @param issue The sca issue
     */
    getIssueLocation(issue: StaticCodeAnalysisIssue): string {
        const lineText = !issue.endLine || issue.startLine === issue.endLine ? ` at line ${issue.startLine}` : ` at lines ${issue.startLine}-${issue.endLine}`;
        let columnText = '';
        if (issue.startColumn) {
            columnText = !issue.endColumn || issue.startColumn === issue.endColumn ? ` column ${issue.startColumn}` : ` columns ${issue.startColumn}-${issue.endColumn}`;
        }
        return issue.filePath + lineText + columnText;
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
                 * If no submission is available, the client will attempt to fetch the build logs anyways.
                 * We catch the error here as it would prevent the displaying of feedback.
                 */
                if (error.status === 403) {
                    return of(null);
                }
                return throwError(error);
            }),
        );
    };

    /**
     * Filters / Summarizes positive test cases for a student and programming exercise result
     * @param feedbackList The list of feedback items
     * @private
     */
    private filterFeedbackItems(feedbackList: FeedbackItem[]) {
        if (this.exerciseType !== ExerciseType.PROGRAMMING || this.showTestDetails) {
            return [...feedbackList];
        } else {
            const positiveTestCasesWithoutDetailText = feedbackList.filter((feedbackItem) => {
                return feedbackItem.type === FeedbackItemType.Test && feedbackItem.positive && !feedbackItem.text;
            });
            if (positiveTestCasesWithoutDetailText.length > 0) {
                return [
                    {
                        type: FeedbackItemType.Test,
                        category: 'Feedback',
                        title: positiveTestCasesWithoutDetailText.length + ' passed test' + (positiveTestCasesWithoutDetailText.length > 1 ? 's' : ''),
                        positive: true,
                        credits: positiveTestCasesWithoutDetailText.reduce((sum, feedbackItem) => sum + (feedbackItem.credits || 0), 0),
                    },
                    ...feedbackList.filter((feedbackItem) => !positiveTestCasesWithoutDetailText.includes(feedbackItem)),
                ];
            } else {
                return [...feedbackList];
            }
        }
    }

    /**
     * Handles the coloring of each feedback items based on its type and credits.
     * @param feedback The feedback item
     */
    getClassNameForFeedbackItem(feedback: FeedbackItem): string {
        if (feedback.type === FeedbackItemType.Issue) {
            return 'alert-warning';
        } else if (feedback.type === FeedbackItemType.Test) {
            return feedback.positive ? 'alert-success' : 'alert-danger';
        } else {
            if (feedback.credits === 0) {
                return 'alert-warning';
            } else {
                return feedback.positive || (feedback.credits && feedback.credits > 0) ? 'alert-success' : 'alert-danger';
            }
        }
    }

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
        const sumAppliedCredits = (sum: number, feedbackItem: FeedbackItem) => sum + (feedbackItem.appliedCredits || 0);

        let testCaseCredits = feedbackList.filter((item) => item.type === FeedbackItemType.Test).reduce(sumCredits, 0);
        const positiveCredits = feedbackList.filter((item) => item.type !== FeedbackItemType.Test && item.credits && item.credits > 0).reduce(sumCredits, 0);

        let codeIssueCredits = -feedbackList.filter((item) => item.type === FeedbackItemType.Issue).reduce(sumAppliedCredits, 0);
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

        const course = getCourseFromExercise(this.exercise!);

        const appliedNegativePoints = roundScoreSpecifiedByCourseSettings(codeIssueCredits + negativeCredits, course);
        const receivedNegativePoints = roundScoreSpecifiedByCourseSettings(codeIssuePenalties + negativeCredits, course);
        const positivePoints = roundScoreSpecifiedByCourseSettings(testCaseCredits + positiveCredits, course);

        if (appliedNegativePoints !== receivedNegativePoints) {
            this.showScoreChartTooltip = true;
        }
        this.setValues(positivePoints, appliedNegativePoints, receivedNegativePoints, maxPoints, maxPointsWithBonus);
    }

    /**
     * Checks if the current result is preliminary and has hidden test cases.
     */
    resultIsPreliminary() {
        return (
            this.result.participation &&
            isProgrammingExerciseStudentParticipation(this.result.participation) &&
            isResultPreliminary(this.result!, this.exercise as ProgrammingExercise)
        );
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
                name: 'Score',
                series: [
                    { name: this.labels[0], value: 0 },
                    { name: this.labels[1], value: 0 },
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
     * Adds a percentage sign to every x axis tick
     * @param tick the default x axis tick
     * @returns string representing custom x axis tick
     */
    xAxisFormatting(tick: string): string {
        return tick + '%';
    }

    /**
     * Handles the event if the user clicks on a legend entry. Then, the corresponding bar should disappear
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
                    this.ngxColors.domain[index] = color !== 'rgba(255,255,255,0)' ? 'rgba(255,255,255,0)' : index === 0 ? '#28a745' : '#dc3545';

                    // update is necessary for the colors to change
                    this.ngxData = [...this.ngxData];
                }
            });
        }
    }
}
