import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { TextSubmission } from 'app/entities/text-submission';
import { Result } from 'app/entities/result';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-file-upload-result',
    templateUrl: './file-upload-result.component.html',
})
export class FileUploadResultComponent {
    public submissionText: string;
    public feedbacks: Feedback[];

    private submission: TextSubmission;

    @Input()
    public set result(result: Result) {
        if (!result || !result.submission || !(result.submission as TextSubmission)) {
            return;
        }

        this.submission = result.submission as TextSubmission;
        this.submissionText = this.submission.text;
        this.feedbacks = result.feedbacks.filter(feedback => feedback.reference);
    }

    constructor(private translateService: TranslateService) {}
}
