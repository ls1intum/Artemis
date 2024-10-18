import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject } from '@angular/core';
import { Feedback, FeedbackType, buildFeedbackTextForReview } from 'app/entities/feedback.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { cloneDeep } from 'lodash-es';
import { TranslateService } from '@ngx-translate/core';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';
import { faBan, faExclamationTriangle, faPencilAlt, faQuestionCircle, faSave, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    private translateService = inject(TranslateService);
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);

    @Input()
    get feedback(): Feedback {
        return this._feedback;
    }
    set feedback(feedback: Feedback | undefined) {
        this._feedback = feedback || new Feedback();
        this.oldFeedback = cloneDeep(this.feedback);
        this.viewOnly = !!feedback;
    }
    private _feedback: Feedback;
    @Input()
    selectedFile: string;
    @Input()
    codeLine: number;
    @Input()
    readOnly: boolean;
    @Input()
    highlightDifferences: boolean;
    @Input()
    course?: Course;
    @ViewChild('detailText') textareaRef: ElementRef;

    @Output()
    onUpdateFeedback = new EventEmitter<Feedback>();
    @Output()
    onCancelFeedback = new EventEmitter<number>();
    @Output()
    onDeleteFeedback = new EventEmitter<Feedback>();
    @Output()
    onEditFeedback = new EventEmitter<number>();

    // Expose the function to the template
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    protected readonly Feedback = Feedback;
    readonly ButtonSize = ButtonSize;
    readonly MANUAL = FeedbackType.MANUAL;

    public elementRef: ElementRef;

    viewOnly: boolean;
    oldFeedback: Feedback;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSave = faSave;
    faBan = faBan;
    faQuestionCircle = faQuestionCircle;
    faPencilAlt = faPencilAlt;
    faTrashAlt = faTrashAlt;
    faExclamationTriangle = faExclamationTriangle;

    constructor() {
        const elementRef = inject(ElementRef);

        this.elementRef = elementRef;
    }

    /**
     * Updates the current feedback and sets props and emits the feedback to parent component
     */
    updateFeedback() {
        this.feedback.type = this.MANUAL;
        this.feedback.reference = `file:${this.selectedFile}_line:${this.codeLine}`;
        if (Feedback.isFeedbackSuggestion(this.feedback)) {
            Feedback.updateFeedbackTypeOnChange(this.feedback);
        } else {
            this.feedback.text = `File ${this.selectedFile} at line ${this.codeLine + 1}`;
        }
        this.viewOnly = true;
        if (this.feedback.credits && this.feedback.credits > 0) {
            this.feedback.positive = true;
        }
        this.onUpdateFeedback.emit(this.feedback);
    }

    /**
     * When an inline feedback already exists, we set it back and display it the viewOnly mode.
     * Otherwise the component is not displayed anymore in the parent component
     */
    cancelFeedback() {
        this.feedback = this.oldFeedback;
        this.viewOnly = this.feedback.type === this.MANUAL;
        this.onCancelFeedback.emit(this.codeLine);
    }

    /**
     * Deletes feedback after confirmation and emits to parent component
     */
    deleteFeedback() {
        this.onDeleteFeedback.emit(this.feedback);
        this.dialogErrorSource.next('');
    }

    /**
     * Checks if component is in view mode and focuses feedback text area
     * @param line Line of code which is emitted to the parent
     */
    editFeedback(line: number) {
        this.viewOnly = false;
        // Save the old feedback in case the user cancels later
        this.oldFeedback = cloneDeep(this.feedback);
        this.onEditFeedback.emit(line);
        setTimeout(() => (this.textareaRef.nativeElement as HTMLTextAreaElement).focus());
    }

    /**
     * Updates the feedback with data of Structured Grading Instructions (SGI)
     * @param event Drop event with SGI data
     */
    updateFeedbackOnDrop(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);
        this.feedback.reference = `file:${this.selectedFile}_line:${this.codeLine}`;
        this.feedback.text = `File ${this.selectedFile} at line ${this.codeLine}`;
    }

    /**
     * Builds the feedback text. When the feedback has a link with grading instruction it merges the feedback of
     * the grading instruction with the feedback text provided by the assessor.
     *
     * @param feedback The feedback for which the text visible to the user should be created.
     * @returns The formatted string representing the feedback text ready to display.
     */
    public buildFeedbackTextForCodeEditor(feedback: Feedback): string {
        return buildFeedbackTextForReview(feedback, false);
    }
}
