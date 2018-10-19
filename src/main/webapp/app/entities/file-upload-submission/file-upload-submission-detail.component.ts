import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

@Component({
    selector: 'jhi-file-upload-submission-detail',
    templateUrl: './file-upload-submission-detail.component.html'
})
export class FileUploadSubmissionDetailComponent implements OnInit {
    fileUploadSubmission: IFileUploadSubmission;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ fileUploadSubmission }) => {
            this.fileUploadSubmission = fileUploadSubmission;
        });
    }

    previousState() {
        window.history.back();
    }
}
