import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback, FeedbackType, MANUAL_ASSESSMENT_IDENTIFIER } from 'app/entities/feedback.model';
import { cloneDeep } from 'lodash';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    @Input()
    get feedback(): Feedback {
        return this._feedback;
    }
    set feedback(feedback: Feedback) {
        this._feedback = feedback || new Feedback();
        this.oldFeedback = cloneDeep(this.feedback);
        this.readOnly = feedback ? true : false;
    }
    private _feedback: Feedback;
    @Input()
    fileName: string;
    @Input()
    codeLine: number;
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback>();
    @Output()
    onCancelFeedback = new EventEmitter<void>();

    readOnly: boolean;
    oldFeedback: Feedback;

    updateFeedback() {
        this.feedback.type = FeedbackType.MANUAL;
        this.feedback.reference = `${MANUAL_ASSESSMENT_IDENTIFIER}_file:${this.fileName}_line:${this.codeLine}`;
        this.readOnly = true;
        this.feedback.text = `Feedback for ${this.fileName} line: ${this.codeLine}`;
        this.onUpdateFeedback.emit(this.feedback);
        // this.onCancelFeedback.emit();
    }

    cancelFeedback() {
        console.log('cancel pressed');
        console.log(this.feedback);
        // The current feedback was not saved yet then do not show the inline feedback component, otherwise show the readonly mode
        if (this.feedback.type !== FeedbackType.MANUAL) {
            this.onCancelFeedback.emit();
        } else {
            // Changes in feedback is discarded
            console.log('feedback: ');
            console.log(this.feedback);
            console.log('feedback old: ');
            console.log(this.oldFeedback);
            this.feedback = this.oldFeedback;
            this.readOnly = true;
        }
    }
}
