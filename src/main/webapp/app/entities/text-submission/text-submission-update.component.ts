import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ITextSubmission } from 'app/shared/model/text-submission.model';
import { TextSubmissionService } from './text-submission.service';

@Component({
    selector: 'jhi-text-submission-update',
    templateUrl: './text-submission-update.component.html'
})
export class TextSubmissionUpdateComponent implements OnInit {
    textSubmission: ITextSubmission;
    isSaving: boolean;

    constructor(private textSubmissionService: TextSubmissionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ textSubmission }) => {
            this.textSubmission = textSubmission;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.textSubmission.id !== undefined) {
            this.subscribeToSaveResponse(this.textSubmissionService.update(this.textSubmission));
        } else {
            this.subscribeToSaveResponse(this.textSubmissionService.create(this.textSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ITextSubmission>>) {
        result.subscribe((res: HttpResponse<ITextSubmission>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
