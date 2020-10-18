import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { cloneDeep } from 'lodash';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    MANUAL = FeedbackType.MANUAL;
    @Input()
    get feedback(): Feedback {
        return this._feedback;
    }
    set feedback(feedback: Feedback) {
        this._feedback = feedback || new Feedback();
        this.oldFeedback = cloneDeep(this.feedback);
        this.editOnly = feedback ? true : false;
    }
    private _feedback: Feedback;
    @Input()
    selectedFile: string;
    @Input()
    codeLine: number;
    @Input()
    readOnly: boolean;
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback>();
    @Output()
    onCancelFeedback = new EventEmitter<number>();
    @Output()
    onDeleteFeedback = new EventEmitter<Feedback>();
    @Output()
    onEditFeedback = new EventEmitter<number>();

    editOnly: boolean;
    oldFeedback: Feedback;
    constructor(private translateService: TranslateService) {}

    /**
     * Updates the current feedback and sets props and emits the feedback to parent component
     */
    updateFeedback() {
        this.feedback.type = this.MANUAL;
        this.feedback.reference = `file:${this.selectedFile}_line:${this.codeLine}`;
        this.editOnly = true;
        this.feedback.text = `File ${this.selectedFile} at line ${this.codeLine}`;
        if (this.feedback.credits && this.feedback.credits > 0) {
            this.feedback.positive = true;
        }
        this.onUpdateFeedback.emit(this.feedback);
    }

    /**
     * When the current feedback was saved, we show the editOnly mode, otherwise the component is not displayed
     * anymore in the parent component
     */
    cancelFeedback() {
        if (this.feedback.type === this.MANUAL) {
            this.feedback = this.oldFeedback;
            this.editOnly = true;
        }
        this.onCancelFeedback.emit(this.codeLine);
    }

    /**
     * Deletes feedback after confirmation and emits to parent component
     */
    deleteFeedback() {
        const text: string = this.translateService.instant('artemisApp.feedback.delete.question', { id: this.feedback.id ?? '' });
        const confirmation = confirm(text);
        if (confirmation) {
            this.onDeleteFeedback.emit(this.feedback);
        }
    }

    /**
     * Checks if component is in edit mode
     * @param line Line of code which is emitted to the parent
     */
    editFeedback(line: number) {
        this.editOnly = false;
        this.onEditFeedback.emit(line);
    }
}
