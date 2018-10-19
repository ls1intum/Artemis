import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';
import { FileUploadSubmissionService } from './file-upload-submission.service';

@Component({
    selector: 'jhi-file-upload-submission-update',
    templateUrl: './file-upload-submission-update.component.html'
})
export class FileUploadSubmissionUpdateComponent implements OnInit {
    fileUploadSubmission: IFileUploadSubmission;
    isSaving: boolean;

    constructor(private fileUploadSubmissionService: FileUploadSubmissionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ fileUploadSubmission }) => {
            this.fileUploadSubmission = fileUploadSubmission;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.fileUploadSubmission.id !== undefined) {
            this.subscribeToSaveResponse(this.fileUploadSubmissionService.update(this.fileUploadSubmission));
        } else {
            this.subscribeToSaveResponse(this.fileUploadSubmissionService.create(this.fileUploadSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IFileUploadSubmission>>) {
        result.subscribe(
            (res: HttpResponse<IFileUploadSubmission>) => this.onSaveSuccess(),
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
