import { Component, Input } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-file-upload-result',
    templateUrl: './file-upload-result.component.html',
})
export class FileUploadResultComponent {
    public feedbacks: Feedback[];

    @Input()
    public set result(result: Result) {
        if (!result || !result.feedbacks) {
            return;
        }
        this.feedbacks = result.feedbacks.filter((feedback) => feedback.credits != undefined);
    }
    constructor() {}
}
