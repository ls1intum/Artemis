import { Component, ElementRef, EventEmitter, Input, Output, inject } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/entities/feedback.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';
import { faCheck, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback-suggestion',
    templateUrl: './code-editor-tutor-assessment-inline-feedback-suggestion.component.html',
    styleUrls: ['./code-editor-tutor-assessment-inline-feedback-suggestion.component.scss'],
})
export class CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent {
    @Input()
    codeLine: number; // Needed for the outer editor to handle the DOM node of the component
    @Input()
    feedback: Feedback;
    @Input()
    course?: Course; // Needed for credit rounding settings

    @Output()
    onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output()
    onDiscardSuggestion = new EventEmitter<Feedback>();

    // Expose functions to the template
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    // Icons
    faCheck = faCheck;
    faTrash = faTrash;

    public elementRef: ElementRef; // Needed for the outer editor to access the DOM node of this component

    constructor() {
        const elementRef = inject(ElementRef);

        this.elementRef = elementRef;
    }
}
