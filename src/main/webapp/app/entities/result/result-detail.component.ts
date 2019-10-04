import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from './';
import { RepositoryService } from 'app/entities/repository';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Feedback } from '../feedback/index';
import { BuildLogEntry, BuildLogEntryArray } from 'app/entities/build-log';
import { tap, catchError, switchMap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { of } from 'rxjs';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
})
export class ResultDetailComponent implements OnInit {
    @Input() result: Result;
    @Input() showTestNames = false;
    isLoading = false;
    loadingFailed = false;
    feedbackList: Feedback[];
    buildLogs: BuildLogEntryArray;

    constructor(public activeModal: NgbActiveModal, private resultService: ResultService, private repositoryService: RepositoryService) {}

    ngOnInit(): void {
        if (this.result.feedbacks && this.result.feedbacks.length > 0) {
            // make sure to reuse existing feedback items and to load feedback at most only once when this component is opened
            this.feedbackList = this.result.feedbacks;
            return;
        }
        this.isLoading = true;
        this.resultService
            .getFeedbackDetailsForResult(this.result.id)
            .pipe(
                switchMap(res => {
                    this.result.feedbacks = res.body!;
                    this.feedbackList = res.body!;
                    if (!this.feedbackList || this.feedbackList.length === 0) {
                        // If we don't have received any feedback, we fetch the buid log outputs
                        return this.repositoryService.buildlogs(this.result.participation!.id).pipe(
                            tap((repoResult: BuildLogEntry[]) => {
                                this.buildLogs = new BuildLogEntryArray(...repoResult);
                            }),
                        );
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
}
