import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { getExercise } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';
import { ScoreChartPreset } from 'app/shared/chart/presets/scoreChartPreset';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import {
    createCommitUrl,
    isProgrammingExerciseParticipation,
    isProgrammingExerciseStudentParticipation,
    isResultPreliminary,
} from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { round } from 'app/shared/util/utils';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

export enum FeedbackItemType {
    Issue,
    Test,
    Feedback,
}

export class FeedbackItem {
    type: FeedbackItemType;
    category: string;
    title?: string;
    text?: string;
    positive?: boolean;
    credits?: number;
    appliedCredits?: number;
}

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
    readonly round = round;

    @Input() result: Result;
    // Specify the feedback.text values that should be shown, all other values will not be visible.
    @Input() feedbackFilter: string[];
    @Input() showTestDetails = false;
    @Input() showScoreChart = false;
    @Input() exerciseType: ExerciseType;

    isLoading = false;
    loadingFailed = false;
    feedbackList: FeedbackItem[];
    filteredFeedbackList: FeedbackItem[];
    buildLogs: BuildLogEntryArray;

    scoreChartPreset: ScoreChartPreset;
    showScoreChartTooltip = false;

    commitHashURLTemplate?: string;

    get exercise(): Exercise | undefined {
        return getExercise(this.result.participation);
    }

    constructor(
        public activeModal: NgbActiveModal,
        private resultService: ResultService,
        private buildLogService: BuildLogService,
        translateService: TranslateService,
        private profileService: ProfileService,
    ) {
        const pointsLabel = translateService.instant('artemisApp.result.chart.points');
        const deductionsLabel = translateService.instant('artemisApp.result.chart.deductions');
        this.scoreChartPreset = new ScoreChartPreset([pointsLabel, deductionsLabel]);
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

        // Get active profiles, to distinguish between Bitbucket and GitLab for the commit link of the result
        this.profileService.getProfileInfo().subscribe((info: ProfileInfo) => {
            this.commitHashURLTemplate = info?.commitHashURLTemplate;
        });
    }

    /**
     * Loads the missing feedback details
     * @param resultId The current result
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
     * Creates a feedback item with a category, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @private
     */
    private createFeedbackItems(feedbacks: Feedback[]): FeedbackItem[] {
        if (this.exerciseType === ExerciseType.PROGRAMMING) {
            return feedbacks.map((feedback) => {
                if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                    const scaCategory = feedback.text!.substring(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
                    const scaIssue = StaticCodeAnalysisIssue.fromFeedback(feedback);
                    return {
                        type: FeedbackItemType.Issue,
                        category: 'Code Issue',
                        title: `${scaCategory} Issue in file ${this.getIssueLocation(scaIssue)}`.trim(),
                        text: this.showTestDetails ? `${scaIssue.rule}: ${scaIssue.message}` : scaIssue.message,
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
                        positive: feedback.positive,
                        credits: feedback.credits,
                    };
                } else {
                    return {
                        type: FeedbackItemType.Feedback,
                        category: this.showTestDetails ? 'Tutor' : 'Feedback',
                        title: feedback.text,
                        text: feedback.detailText,
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

        const appliedNegativePoints = codeIssueCredits + negativeCredits;
        const receivedNegativePoints = codeIssuePenalties + negativeCredits;
        const positivePoints = testCaseCredits + positiveCredits;

        if (appliedNegativePoints !== receivedNegativePoints) {
            this.showScoreChartTooltip = true;
        }

        // the chart preset handles the capping to the maximum score of the exercise
        this.scoreChartPreset.setValues(positivePoints, appliedNegativePoints, receivedNegativePoints, maxPoints, maxPointsWithBonus);
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

    getCommitUrl(): string {
        const projectKey = (this.exercise as ProgrammingExercise)?.projectKey;
        const programmingSubmission = this.result.submission as ProgrammingSubmission;
        return createCommitUrl(this.commitHashURLTemplate, projectKey, this.result.participation, programmingSubmission);
    }
}
