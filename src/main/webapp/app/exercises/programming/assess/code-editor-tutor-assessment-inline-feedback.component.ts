import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    @Input()
    feedback: Feedback = new Feedback();
    @Input()
    readOnly = false;
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback>();
    @Output()
    onCancelFeedback = new EventEmitter<void>();
    updateFeedback() {
        this.feedback.type = FeedbackType.MANUAL;
        this.readOnly = true;
        this.onUpdateFeedback.emit(this.feedback);
        // this.onCancelFeedback.emit();
    }

    cancelFeedback() {
        // The current feedback was not saved yet then do not show the inline feedback component, otherwise show the readonly mode
        if (this.feedback.type !== FeedbackType.MANUAL) {
            this.onCancelFeedback.emit();
        } else {
            this.readOnly = true;
        }
    }
}
