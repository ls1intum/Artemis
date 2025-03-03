import { Component, Input } from '@angular/core';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { FileUploadSubmissionComponent } from 'app/exercises/file-upload/participate/file-upload-submission.component';

@Component({
    selector: 'jhi-file-upload-exam-summary',
    templateUrl: './file-upload-exam-summary.component.html',
    imports: [FileUploadSubmissionComponent],
})
export class FileUploadExamSummaryComponent {
    @Input() submission: FileUploadSubmission;
    @Input() exercise: Exercise;
    @Input() expandProblemStatement = false;
    @Input() isAfterResultsArePublished = false;
}
