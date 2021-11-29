import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-unreferenced-feedback',
    templateUrl: './unreferenced-feedback.component.html',
    styleUrls: [],
})
export class UnreferencedFeedbackComponent {
    FeedbackType = FeedbackType;

    unreferencedFeedback: Feedback[] = [];
    assessmentsAreValid: boolean;

    @Input() busy: boolean;
    @Input() readOnly: boolean;
    @Input() highlightDifferences: boolean;

    /**
     * In order to make it possible to mark unreferenced feedback based on the correction status, we assign reference ids to the unreferenced feedback
     */
    @Input() addReferenceIdForExampleSubmission = false;

    @Input() set feedbacks(feedbacks: Feedback[]) {
        this.unreferencedFeedback = [...feedbacks];
    }

    @Output() feedbacksChange = new EventEmitter<Feedback[]>();

    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.unreferencedFeedback.indexOf(assessmentToDelete);
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

    updateAssessment(feedback: Feedback) {
        const indexToUpdate = this.unreferencedFeedback.indexOf(feedback);
        this.unreferencedFeedback[indexToUpdate] = feedback;
        this.validateFeedback();
        this.feedbacksChange.emit(this.unreferencedFeedback);
    }

    public addUnreferencedFeedback(): void {
        const feedback = new Feedback();
        feedback.credits = 0;
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;

        if (this.addReferenceIdForExampleSubmission) {
            feedback.reference = (this.unreferencedFeedback.length + 1).toString();
        }

        this.unreferencedFeedback.push(feedback);
        this.validateFeedback();
        this.feedbacksChange.emit(this.unreferencedFeedback);
    }
}
