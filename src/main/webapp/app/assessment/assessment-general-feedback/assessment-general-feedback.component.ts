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
    allowDrop(event: DragEvent) {
        event.preventDefault();
    }
    drop(event: any) {
        event.preventDefault();
        const data = event.dataTransfer.getData('text');
        const instruction = JSON.parse(data);
        const credits = instruction.credits;
        const feedback = instruction.feedback;
        if (credits > 0) {
            this.text += 'Score: ' + '+' + credits + ' Feedback: ' + feedback + '\n';
        } else {
            this.text += 'Score: ' + credits + ' Feedback: ' + feedback + '\n';
        }
        this.onTextChange(this.text);
    }
}
