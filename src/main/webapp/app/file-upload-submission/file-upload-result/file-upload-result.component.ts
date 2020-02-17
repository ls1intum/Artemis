import { Component, Input } from '@angular/core';
import { Result } from 'app/entities/result/result.model';
import { partition } from 'lodash';
import { Feedback } from 'app/entities/feedback/feedback.model';

@Component({
    selector: 'jhi-file-upload-result',
    templateUrl: './file-upload-result.component.html',
})
export class FileUploadResultComponent {
    public feedbacks: Feedback[];
    public generalFeedback: Feedback | null;

    @Input()
    public set result(result: Result) {
        if (!result) {
            return;
        }
        const [feedbackWithCredits, feedbackWithoutCredits] = partition(result.feedbacks, feedback => feedback.credits !== 0);
        this.feedbacks = feedbackWithCredits;
        this.generalFeedback = feedbackWithoutCredits[0] ? feedbackWithoutCredits[0] : null;
    }
    constructor() {}
}
