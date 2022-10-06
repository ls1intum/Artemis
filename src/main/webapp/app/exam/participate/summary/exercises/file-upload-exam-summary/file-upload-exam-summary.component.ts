import { Component, Input, OnInit } from '@angular/core';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileService } from 'app/shared/http/file.service';

@Component({
    selector: 'jhi-file-upload-exam-summary',
    templateUrl: './file-upload-exam-summary.component.html',
})
export class FileUploadExamSummaryComponent implements OnInit {
    @Input()
    submission: FileUploadSubmission;

    constructor(private fileService: FileService) {}

    ngOnInit(): void {}

    /**
     *
     * @param filePath
     * File Upload Exercise
     */
    downloadFile(filePath: string | undefined) {
        if (!filePath) {
            return;
        }
        this.fileService.downloadFile(filePath);
    }

    attachmentExtension(filePath: string): string {
        if (!filePath) {
            return 'N/A';
        }

        return filePath.split('.').pop()!;
    }
}
