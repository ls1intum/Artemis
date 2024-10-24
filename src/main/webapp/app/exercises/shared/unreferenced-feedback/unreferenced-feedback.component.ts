import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER, Feedback, FeedbackType } from 'app/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

@Component({
    selector: 'jhi-unreferenced-feedback',
    templateUrl: './unreferenced-feedback.component.html',
    styleUrls: [],
})
export class UnreferencedFeedbackComponent {
    FeedbackType = FeedbackType;

    unreferencedFeedback: Feedback[] = [];
    assessmentsAreValid: boolean;
    feedbackDetailChanges: boolean = false;

    @Input() readOnly: boolean;
    @Input() highlightDifferences: boolean;
    @Input() useDefaultFeedbackSuggestionBadgeText: boolean = false;
    @Input() resultId: number;

    /**
     * In order to make it possible to mark unreferenced feedback based on the correction status, we assign reference ids to the unreferenced feedback
     */
    @Input() addReferenceIdForExampleSubmission = false;

    @Input() set feedbacks(feedbacks: Feedback[]) {
        this.unreferencedFeedback = [...feedbacks];
    }

    @Input() feedbackSuggestions: Feedback[] = [];

    @Output() feedbacksChange = new EventEmitter<Feedback[]>();
    @Output() onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output() onDiscardSuggestion = new EventEmitter<Feedback>();

    constructor(private structuredGradingCriterionService: StructuredGradingCriterionService) {}

    public deleteFeedback(feedbackToDelete: Feedback): void {
        const indexToDelete = this.unreferencedFeedback.indexOf(feedbackToDelete);
        this.unreferencedFeedback.splice(indexToDelete, 1);
        this.feedbacksChange.emit(this.unreferencedFeedback);
        this.validateFeedback();
    }
    /**
     * Validates the feedback:
     *   - There must be any form of feedback, either general feedback or feedback referencing a model element or both
     *   - Each reference feedback must have a score that is a valid number
     */
    validateFeedback() {
        if (!this.unreferencedFeedback || this.unreferencedFeedback.length === 0) {
            this.assessmentsAreValid = false;
            return;
        }
        for (const feedback of this.unreferencedFeedback) {
            if (feedback.credits == undefined || isNaN(feedback.credits)) {
                this.assessmentsAreValid = false;
                return;
            }
        }
        this.assessmentsAreValid = true;
    }

    /**
     * Update the feedback in the list of unreferenced feedback, changing or adding it.
     * @param feedback The feedback to update
     */
    updateFeedback(feedback: Feedback) {
        this.feedbackDetailChanges = true;
        const indexToUpdate = this.unreferencedFeedback.indexOf(feedback);
        if (indexToUpdate < 0) {
            this.unreferencedFeedback.push(feedback);
        } else {
            this.unreferencedFeedback[indexToUpdate] = feedback;
        }
        this.validateFeedback();
        this.feedbacksChange.emit(this.unreferencedFeedback);
    }

    public addUnreferencedFeedback(): void {
        const feedback = new Feedback();
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;

        // Assign the next id to the unreferenced feedback
        if (this.addReferenceIdForExampleSubmission) {
            feedback.reference = this.generateNewUnreferencedFeedbackReference().toString();
        }

        this.unreferencedFeedback.push(feedback);
        this.validateFeedback();
        this.feedbacksChange.emit(this.unreferencedFeedback);
    }

    /**
     * Generate the new reference, by computing what is currently the maximum reference within all feedback and add 1
     */
    private generateNewUnreferencedFeedbackReference(): number {
        if (this.unreferencedFeedback.length === 0) {
            return 1;
        }

        const references = this.unreferencedFeedback.map((feedback) => {
            const id = +(feedback.reference ?? '0');
            if (isNaN(id)) {
                return 0;
            }
            return id;
        });
        return Math.max(...references.concat([0])) + 1;
    }

    /**
     * Accept a feedback suggestion: Make it "real" feedback and remove the suggestion card
     */
    acceptSuggestion(feedback: Feedback) {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((f) => f !== feedback); // Remove the suggestion card
        // We need to change the feedback type to "manual" because non-manual feedback is never editable in the editor
        // and will be filtered out in all kinds of places
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        // Change the prefix "FeedbackSuggestion:" to "FeedbackSuggestion:accepted:"
        feedback.text = (feedback.text ?? FEEDBACK_SUGGESTION_IDENTIFIER).replace(FEEDBACK_SUGGESTION_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER);
        this.updateFeedback(feedback); // Make it "real" feedback
        this.onAcceptSuggestion.emit(feedback);
    }

    /**
     * Discard a feedback suggestion: Remove the suggestion card and emit the event
     */
    discardSuggestion(feedback: Feedback) {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((f) => f !== feedback); // Remove the suggestion card
        this.onDiscardSuggestion.emit(feedback);
    }

    createAssessmentOnDrop(event: Event) {
        if (this.feedbackDetailChanges) {
            this.feedbackDetailChanges = false;
            return;
        }
        this.addUnreferencedFeedback();
        const newFeedback: Feedback | undefined = this.unreferencedFeedback.last();
        if (newFeedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(newFeedback, event);
            this.updateFeedback(newFeedback);
            this.feedbackDetailChanges = false;
        }
    }
}
