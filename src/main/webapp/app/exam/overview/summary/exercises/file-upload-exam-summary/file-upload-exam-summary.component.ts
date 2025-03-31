import { Component, Input } from '@angular/core';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission.component';

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
