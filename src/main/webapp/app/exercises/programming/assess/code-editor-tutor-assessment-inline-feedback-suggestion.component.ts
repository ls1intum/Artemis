import { Component, ElementRef, EventEmitter, Input, Output } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/entities/feedback.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';
import { faCheck, faLightbulb, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback-suggestion',
    templateUrl: './code-editor-tutor-assessment-inline-feedback-suggestion.component.html',
    styleUrls: ['./code-editor-tutor-assessment-inline-feedback-suggestion.component.scss'],
})
export class CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent {
    @Input()
    feedback: Feedback;
    @Input()
    codeLine: number;
    @Input()
    course?: Course; // needed for credit rounding settings

    @Output()
    onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output()
    onRejectSuggestion = new EventEmitter<Feedback>();

    // Expose functions to the template
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    // Icons
    faLightbulb = faLightbulb;
    faCheck = faCheck;
    faTrash = faTrash;

    public elementRef: ElementRef;

    constructor(elementRef: ElementRef) {
        this.elementRef = elementRef;
    }
}
