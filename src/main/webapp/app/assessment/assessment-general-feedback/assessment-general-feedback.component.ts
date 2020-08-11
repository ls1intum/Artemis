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

    @Input() readOnly: boolean;
    @Input() set feedback(feedback: Feedback) {
        this.feedbackClone = Object.assign({}, feedback);
        this.text = feedback.detailText || '';
    }

    @Output() feedbackChange = new EventEmitter<Feedback>();

    /**
     * Emits the change of the general feedback detail text to the parent component
     */
    public onTextChange(text: string): void {
        const feedbackText = text;
        this.feedbackClone.detailText = feedbackText.length > 0 ? feedbackText : null;
        this.feedbackChange.emit(this.feedbackClone);
    }
}
