import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { cloneDeep } from 'lodash';
import { TranslateService } from '@ngx-translate/core';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

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
        this.viewOnly = feedback ? true : false;
        if (this._feedback.gradingInstruction && this._feedback.gradingInstruction.usageCount !== 0) {
            this.disableEditScore = true;
        } else {
            this.disableEditScore = false;
        }
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

    viewOnly: boolean;
    oldFeedback: Feedback;
    disableEditScore = false;
    constructor(private translateService: TranslateService, public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    /**
     * Updates the current feedback and sets props and emits the feedback to parent component
     */
    updateFeedback() {
        this.feedback.type = this.MANUAL;
        this.feedback.reference = `file:${this.selectedFile}_line:${this.codeLine}`;
        this.feedback.text = `File ${this.selectedFile} at line ${this.codeLine + 1}`;
        this.viewOnly = true;
        if (this.feedback.credits && this.feedback.credits > 0) {
            this.feedback.positive = true;
        }
        this.onUpdateFeedback.emit(this.feedback);
    }

    /**
     * When a inline feedback already exists, we set it back and display it the viewOnly mode.
     * Otherwise the component is not displayed anymore.
     * anymore in the parent component
     */
    cancelFeedback() {
        this.feedback = this.oldFeedback;
        this.viewOnly = false;
        if (this.feedback.type === this.MANUAL) {
            this.viewOnly = true;
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
     * Checks if component is in view mode
     * @param line Line of code which is emitted to the parent
     */
    editFeedback(line: number) {
        this.viewOnly = false;
        this.onEditFeedback.emit(line);
    }

    /**
     * Updates the feedback with data of Structured Grading Instructions (SGI)
     * @param event Drop event with SGI data
     */
    updateFeedbackOnDrop(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);
        if (this.feedback.gradingInstruction && this.feedback.gradingInstruction.usageCount !== 0) {
            this.disableEditScore = true;
        } else {
            this.disableEditScore = false;
        }
        this.feedback.reference = `file:${this.selectedFile}_line:${this.codeLine}`;
        this.feedback.text = `File ${this.selectedFile} at line ${this.codeLine}`;
    }
}
