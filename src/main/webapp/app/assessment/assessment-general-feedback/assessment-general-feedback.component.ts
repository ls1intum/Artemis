import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-assessment-general-feedback',
    templateUrl: './assessment-general-feedback.component.html',
    styleUrls: [],
})
export class AssessmentGeneralFeedbackComponent {
    private feedbackClone: Feedback;
    public text: string;

    @Input() set feedback(feedback: Feedback) {
        this.feedbackClone = Object.assign({}, feedback);
        this.text = feedback.detailText || '';
    }

    @Output() feedbackChange = new EventEmitter<Feedback>();

    public onTextChange(text: string): void {
        const trimmedText = text.trim();
        this.feedbackClone.detailText = trimmedText.length > 0 ? trimmedText : null;
        this.feedbackChange.emit(this.feedbackClone);
    }
}
