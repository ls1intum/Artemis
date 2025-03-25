import { Component, Input } from '@angular/core';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { Component, input } from '@angular/core';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission.component';

@Component({
    selector: 'jhi-file-upload-exam-summary',
    templateUrl: './file-upload-exam-summary.component.html',
    imports: [FileUploadSubmissionComponent],
})
export class FileUploadExamSummaryComponent {
    submission = input.required<FileUploadSubmission>();
    exercise = input.required<Exercise>();
    expandProblemStatement = input(false);
    isAfterResultsArePublished = input(false);
}
