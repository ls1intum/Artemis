import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of, throwError } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';

export class FeedbackItem {
    category: string;
    title: string | null;
    text: string | null;
    positive: boolean | null;
    credits: number;
}

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
    styleUrls: ['./result-detail.scss'],
})
export class ResultDetailComponent implements OnInit {
    BuildLogType = BuildLogType;

    @Input() result: Result;
    // Specify the feedback.text values that should be shown, all other values will not be visible.
    @Input() feedbackFilter: string[];
    @Input() showTestNames = false;
    @Input() showFeedbackCredits = false;
    @Input() exerciseType: ExerciseType;

    isLoading = false;
    loadingFailed = false;
    feedbackList: FeedbackItem[];
    filteredFeedbackList: FeedbackItem[];
    buildLogs: BuildLogEntryArray;

    constructor(public activeModal: NgbActiveModal, private resultService: ResultService, private buildLogService: BuildLogService) {}

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
                switchMap((feedbacks: Feedback[] | undefined | null) => (feedbacks && feedbacks.length ? of(feedbacks) : this.getFeedbackDetailsForResult(this.result.id!))),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    /*
                     * If we have feedback, filter it if needed, distinguish between test case and static code analysis
                     * feedback and assign the lists to the component
                     */
                    if (feedbacks && feedbacks.length) {
                        this.result.feedbacks = feedbacks!;
                        const filteredFeedback = this.filterFeedback(feedbacks);
                        this.feedbackList = this.createFeedbackItems(filteredFeedback);
                        this.filteredFeedbackList = this.filterFeedbackItems(this.feedbackList);
                    }
                    // If we don't receive a submission or the submission is marked with buildFailed, fetch the build logs.
                    if (this.exerciseType === ExerciseType.PROGRAMMING && (!this.result.submission || (this.result.submission as ProgrammingSubmission).buildFailed)) {
                        return this.fetchAndSetBuildLogs(this.result.participation!.id!);
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

    private getFeedbackDetailsForResult(resultId: number) {
        return this.resultService.getFeedbackDetailsForResult(resultId).pipe(map(({ body: feedbackList }) => feedbackList!));
    }

    private filterFeedback = (feedbackList: Feedback[]) => {
        if (!this.feedbackFilter) {
            return [...feedbackList];
        } else {
            return this.feedbackFilter
                .map((test) => {
                    return feedbackList.find(({ text }) => text === test);
                })
                .filter(Boolean) as Feedback[];
        }
    };

    private createFeedbackItems(feedbacks: Feedback[]): FeedbackItem[] {
        if (this.exerciseType === ExerciseType.PROGRAMMING) {
            return feedbacks.map((feedback) => {
                if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                    const scaCategory = feedback.text!.substring(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
                    const scaIssue = StaticCodeAnalysisIssue.fromFeedback(feedback);
                    return {
                        category: 'Code Issue',
                        title: `${scaCategory} Issue in file ${this.getIssueLocation(scaIssue)}`.trim(),
                        text: `${scaIssue.rule}: ${scaIssue.message}`,
                        positive: false,
                        credits: feedback.credits,
                    };
                } else if (feedback.type === 'AUTOMATIC') {
                    return {
                        category: 'Test Case',
                        title: !this.showTestNames
                            ? `${feedback.positive ? 'Passed' : 'Failed'} test`
                            : feedback.positive === undefined
                            ? `No result information for ${feedback.text}`
                            : `Test ${feedback.text} ${feedback.positive ? 'passed' : 'failed'}`,
                        text: feedback.detailText,
                        positive: feedback.positive,
                        credits: feedback.credits,
                    };
                } else {
                    return {
                        category: 'Tutor',
                        title: feedback.text,
                        text: feedback.detailText,
                        positive: feedback.positive,
                        credits: feedback.credits,
                    };
                }
            });
        } else {
            return feedbacks.map((feedback) => ({
                category: feedback.type === 'AUTOMATIC' ? 'System' : 'Tutor',
                title: feedback.text,
                text: feedback.detailText,
                positive: feedback.positive,
                credits: feedback.credits,
            }));
        }
    }

    getIssueLocation(issue: StaticCodeAnalysisIssue): string {
        const lineText = issue.startLine === issue.endLine ? ` at line ${issue.startLine}` : ` at lines ${issue.startLine}-${issue.endLine}`;
        let columnText = '';
        if (issue.startColumn) {
            columnText = issue.startColumn === issue.endColumn ? ` column ${issue.startColumn}` : ` columns ${issue.startColumn}-${issue.endColumn}`;
        }
        return issue.filePath + lineText + columnText;
    }

    private fetchAndSetBuildLogs = (participationId: number) => {
        return this.buildLogService.getBuildLogs(participationId).pipe(
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

    private filterFeedbackItems(feedbackList: FeedbackItem[]) {
        if (this.exerciseType === ExerciseType.PROGRAMMING) {
            return feedbackList.filter((feedbackItem) => {
                if (feedbackItem.category === 'Test Case' && feedbackItem.positive) {
                    return false;
                }
                return true;
            });
        } else {
            return [...feedbackList];
        }
    }

    getClassNameForFeedbackItem(feedback: FeedbackItem): string {
        if (feedback.category === 'Test Case') {
            return 'alert-danger';
        } else if (feedback.category === 'Code Issue') {
            return 'alert-warning';
        } else if (this.exerciseType === ExerciseType.PROGRAMMING) {
            return 'alert-primary';
        } else {
            return feedback.positive ? 'alert-success' : 'alert-danger';
        }
    }
}
