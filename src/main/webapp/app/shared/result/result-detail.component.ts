import { Component, Input, OnInit } from '@angular/core';
import { RepositoryService } from 'app/shared/result/repository.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/entities/build-log.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

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
    buildLogs: BuildLogEntryArray;

    constructor(public activeModal: NgbActiveModal, private resultService: ResultService, private repositoryService: RepositoryService) {}

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
                    // If we don't have received any feedback, we fetch the build log outputs for programming exercises.
                    if (this.exerciseType === ExerciseType.PROGRAMMING && (!feedbacks || !feedbacks.length)) {
                        return this.fetchAndSetBuildLogs(this.result.participation!.id);
                    } else if (feedbacks && feedbacks.length) {
                        // If we have feedback, filter it if needed and assign it to the component.
                        this.filterAndSetFeedbacks(feedbacks);
                    }
                    return of(null);
                }),
                catchError((error: HttpErrorResponse) => {
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

    private filterAndSetFeedbacks = (feedbackList: Feedback[]) => {
        // TODO: The input object is mutated, this could lead to unexpected bugs.
        this.result.feedbacks = feedbackList!;
        if (!this.feedbackFilter) {
            this.feedbackList = feedbackList;
        } else {
            this.feedbackList =
                this.feedbackFilter
                    .map((test) => {
                        return feedbackList.find(({ text }) => text === test);
                    })
                    .filter(Boolean) as Feedback[];
        }
    };

    private fetchAndSetBuildLogs = (participationId: number) => {
        return this.repositoryService.buildlogs(participationId).pipe(
            tap((repoResult: BuildLogEntry[]) => {
                this.buildLogs = BuildLogEntryArray.fromBuildLogs(repoResult);
            }),
        );
    };
}
