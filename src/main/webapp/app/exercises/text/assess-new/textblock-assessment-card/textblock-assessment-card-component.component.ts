import { Component, Input, Output, EventEmitter, ViewChild } from '@angular/core';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { Feedback } from 'app/entities/feedback.model';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';

type OptionalTextBlockRef = TextBlockRef | null;

@Component({
    selector: 'jhi-textblock-assessment-card-component',
    templateUrl: './textblock-assessment-card-component.component.html',
    styleUrls: ['./textblock-assessment-card-component.component.scss'],
})
export class TextblockAssessmentCardComponentComponent {
    @Input() textBlockRef: TextBlockRef;
    @Input() selected = false;
    @Output() didSelect = new EventEmitter<OptionalTextBlockRef>();
    @Output() didChange = new EventEmitter<TextBlockRef>();
    @ViewChild(TextblockFeedbackEditorComponent) feedbackEditor: TextblockFeedbackEditorComponent;

    select(autofocus = true): void {
        this.didSelect.emit(this.textBlockRef);
        this.textBlockRef.initFeedback();

        if (autofocus) {
            setTimeout(() => this.feedbackEditor.focus());
        }
    }

    unselect(): void {
        this.didSelect.emit(null);
        delete this.textBlockRef.feedback;
        this.feedbackDidChange();
    }

    feedbackDidChange(): void {
        this.didChange.emit(this.textBlockRef);
    }
}
