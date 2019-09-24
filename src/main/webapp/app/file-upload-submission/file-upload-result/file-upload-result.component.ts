import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { Result } from 'app/entities/result';
import { partition } from 'lodash';

@Component({
    selector: 'jhi-file-upload-result',
    templateUrl: './file-upload-result.component.html',
})
export class FileUploadResultComponent {
    public feedbacks: Feedback[];
    public generalFeedback: Feedback;

    @Input()
    public set result(result: Result) {
        if (!result) {
            return;
        }
        const groupedFeedback = partition(result.feedbacks, feedback => feedback.credits > 0);
        this.feedbacks = groupedFeedback[0];
        this.generalFeedback = groupedFeedback[1][0];
    }
    constructor() {}
}
