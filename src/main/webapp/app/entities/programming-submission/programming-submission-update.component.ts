import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IProgrammingSubmission } from 'app/shared/model/programming-submission.model';
import { ProgrammingSubmissionService } from './programming-submission.service';

@Component({
    selector: 'jhi-programming-submission-update',
    templateUrl: './programming-submission-update.component.html'
})
export class ProgrammingSubmissionUpdateComponent implements OnInit {
    programmingSubmission: IProgrammingSubmission;
    isSaving: boolean;

    constructor(private programmingSubmissionService: ProgrammingSubmissionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ programmingSubmission }) => {
            this.programmingSubmission = programmingSubmission;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.programmingSubmission.id !== undefined) {
            this.subscribeToSaveResponse(this.programmingSubmissionService.update(this.programmingSubmission));
        } else {
            this.subscribeToSaveResponse(this.programmingSubmissionService.create(this.programmingSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IProgrammingSubmission>>) {
        result.subscribe(
            (res: HttpResponse<IProgrammingSubmission>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError()
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
