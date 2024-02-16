import { Component, Input } from '@angular/core';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
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
}
