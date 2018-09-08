import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from './submission.service';

@Component({
    selector: 'jhi-submission-update',
    templateUrl: './submission-update.component.html'
})
export class SubmissionUpdateComponent implements OnInit {
    private _submission: ISubmission;
    isSaving: boolean;

    constructor(private submissionService: SubmissionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ submission }) => {
            this.submission = submission;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.submission.id !== undefined) {
            this.subscribeToSaveResponse(this.submissionService.update(this.submission));
        } else {
            this.subscribeToSaveResponse(this.submissionService.create(this.submission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ISubmission>>) {
        result.subscribe((res: HttpResponse<ISubmission>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
    get submission() {
        return this._submission;
    }

    set submission(submission: ISubmission) {
        this._submission = submission;
    }
}
