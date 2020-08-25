import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
})
export class ResultDetailComponent implements OnInit {
    BuildLogType = BuildLogType;

    @Input() result: Result;
    // Specify the feedback.text values that should be shown, all other values will not be visible.
    @Input() feedbackFilter: string[];
    @Input() showTestNames = false;
    @Input() exerciseType: ExerciseType;
    isLoading = false;
    loadingFailed = false;
    feedbackList: Feedback[];
    staticCodeAnalysisFeedbackList: Feedback[];
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
                switchMap((feedbacks: Feedback[] | undefined | null) => (feedbacks && feedbacks.length ? of(feedbacks) : this.getFeedbackDetailsForResult(this.result.id))),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    /*
                     * If we have feedback, filter it if needed, distinguish between test case and static code analysis
                     * feedback and assign the lists to the component
                     */
                    if (feedbacks && feedbacks.length) {
                        const filteredFeedback = this.filterFeedback(feedbacks);
                        if (this.exerciseType === ExerciseType.PROGRAMMING) {
                            this.partitionAndSetFeedback(filteredFeedback);
                        } else {
                            this.feedbackList = filteredFeedback;
                        }
                    }
                    // If we don't receive a submission or the submission is marked with buildFailed, fetch the build logs.
                    if (this.exerciseType === ExerciseType.PROGRAMMING && (!this.result.submission || (this.result.submission as ProgrammingSubmission).buildFailed)) {
                        return this.fetchAndSetBuildLogs(this.result.participation!.id);
                    }
                    return of(null);
                }),
                catchError(() => {
                    // TODO: When the server would give better error information, we could improve the UI.
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
        // TODO: The input object is mutated, this could lead to unexpected bugs.
        this.result.feedbacks = feedbackList!;
        if (!this.feedbackFilter) {
            return feedbackList;
        } else {
            return this.feedbackFilter
                .map((test) => {
                    return feedbackList.find(({ text }) => text === test);
                })
                .filter(Boolean) as Feedback[];
        }
    };

    /**
     * Distinguishes between static code analysis feedback and test case feedback.
     * Assigns both lists to the component.
     *
     * @param feedbackList All available Feedback
     */
    private partitionAndSetFeedback(feedbackList: Feedback[]) {
        const testCaseFeedback: Feedback[] = [];
        const staticCodeAnalysisFeedback: Feedback[] = [];
        feedbackList.forEach((feedback) => {
            if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                staticCodeAnalysisFeedback.push(feedback);
            } else {
                testCaseFeedback.push(feedback);
            }
        });
        this.feedbackList = testCaseFeedback;
        this.staticCodeAnalysisFeedbackList = staticCodeAnalysisFeedback;
    }

    private fetchAndSetBuildLogs = (participationId: number) => {
        return this.buildLogService.getBuildLogs(participationId).pipe(
            tap((repoResult: BuildLogEntry[]) => {
                this.buildLogs = BuildLogEntryArray.fromBuildLogs(repoResult);
            }),
        );
    };
}
