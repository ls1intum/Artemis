import { Component, Input } from '@angular/core';
import { FileDetails } from 'app/entities/file-details.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileService } from 'app/shared/http/file.service';

@Component({
    selector: 'jhi-file-upload-exam-summary',
    templateUrl: './file-upload-exam-summary.component.html',
})
export class FileUploadExamSummaryComponent {
    @Input()
    submission: FileUploadSubmission;

    constructor(private fileService: FileService) {}

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

    protected readonly FileDetails = FileDetails;
}
