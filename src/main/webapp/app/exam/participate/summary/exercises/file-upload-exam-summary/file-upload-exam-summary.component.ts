import { Component, Input } from '@angular/core';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileService } from 'app/shared/http/file.service';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-file-upload-exam-summary',
    templateUrl: './file-upload-exam-summary.component.html',
})
export class FileUploadExamSummaryComponent {
    @Input() submission: FileUploadSubmission;

    @Input() exercise: Exercise;

    @Input() expandProblemStatement?: boolean = false;

    @Input() isAfterResultsArePublished?: boolean = false;

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

    attachmentExtension(filePath: string): string {
        if (!filePath) {
            return 'N/A';
        }

        return filePath.split('.').pop()!;
    }
}
