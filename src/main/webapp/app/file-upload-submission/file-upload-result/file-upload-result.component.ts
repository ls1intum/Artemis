import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { Result } from 'app/entities/result';

@Component({
    selector: 'jhi-file-upload-result',
    templateUrl: './file-upload-result.component.html',
})
export class FileUploadResultComponent {
    public feedbacks: Feedback[];

    @Input()
    public set result(result: Result) {
        if (!result) {
            return;
        }
        this.feedbacks = result.feedbacks.filter(feedback => feedback.reference);
    }
    constructor() {}
}
