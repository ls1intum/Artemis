import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IModelingSubmission } from 'app/shared/model/modeling-submission.model';
import { ModelingSubmissionService } from './modeling-submission.service';

@Component({
    selector: 'jhi-modeling-submission-update',
    templateUrl: './modeling-submission-update.component.html'
})
export class ModelingSubmissionUpdateComponent implements OnInit {
    modelingSubmission: IModelingSubmission;
    isSaving: boolean;

    constructor(private modelingSubmissionService: ModelingSubmissionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ modelingSubmission }) => {
            this.modelingSubmission = modelingSubmission;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.modelingSubmission.id !== undefined) {
            this.subscribeToSaveResponse(this.modelingSubmissionService.update(this.modelingSubmission));
        } else {
            this.subscribeToSaveResponse(this.modelingSubmissionService.create(this.modelingSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IModelingSubmission>>) {
        result.subscribe((res: HttpResponse<IModelingSubmission>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
