import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from './';
import { RepositoryService } from 'app/entities/repository';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Feedback } from '../feedback/index';
import { BuildLogEntry, BuildLogEntryArray } from 'app/entities/build-log';
import { tap, catchError, switchMap, map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { of } from 'rxjs';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
})
export class ResultDetailComponent implements OnInit {
    @Input() result: Result;
    // Specify the feedback.text values that should be shown, all other values will not be visible.
    @Input() feedbackFilter: string[];
    @Input() showTestNames = false;
    isLoading = false;
    loadingFailed = false;
    feedbackList: Feedback[];
    buildLogs: BuildLogEntryArray;

    constructor(public activeModal: NgbActiveModal, private resultService: ResultService, private repositoryService: RepositoryService) {}

    ngOnInit(): void {
        this.isLoading = true;
        of(this.result.feedbacks)
            .pipe(
                // If the result already has feedbacks assigned to it, don't query the server.
                switchMap((feedbacks: Feedback[] | undefined | null) => (feedbacks && feedbacks.length ? of(feedbacks) : this.getFeedbackDetailsForResult(this.result.id))),
                switchMap((feedbacks: Feedback[] | undefined | null) => {
                    if (!feedbacks || !feedbacks.length) {
                        // If we don't have received any feedback, we fetch the build log outputs
                        return this.repositoryService.buildlogs(this.result.participation!.id).pipe(
                            tap((repoResult: BuildLogEntry[]) => {
                                this.buildLogs = new BuildLogEntryArray(...repoResult);
                            }),
                        );
                    }
                    // If we have feedback, filter it if needed and assign it to the component.
                    this.filterAndSetFeedbacks(feedbacks);
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
            this.feedbackList = this.feedbackFilter
                .map(test => {
                    return feedbackList.find(({ text }) => text === test);
                })
                .filter(Boolean) as Feedback[];
        }
    };
}
