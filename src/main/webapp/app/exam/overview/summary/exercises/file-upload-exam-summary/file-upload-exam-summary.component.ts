import { EditorViewingPurpose } from 'app/exercise/shared/exercise-submission.interface';
import { Component, input } from '@angular/core';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission/file-upload-submission.component';

@Component({
    selector: 'jhi-file-upload-exam-summary',
    templateUrl: './file-upload-exam-summary.component.html',
    imports: [FileUploadSubmissionComponent],
})
export class FileUploadExamSummaryComponent {
    readonly submission = input<FileUploadSubmission>(undefined!);
    readonly viewingPurpose = input<EditorViewingPurpose>('owner');
    readonly exercise = input<Exercise>(undefined!);
    readonly expandProblemStatement = input(false);
    readonly isAfterResultsArePublished = input(false);
}
