import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-unreferenced-feedback',
    templateUrl: './unreferenced-feedback.component.html',
})
export class UnreferencedFeedbackComponent {
    unreferencedFeedback: Feedback[] = [];
    @Input() assessmentsAreValid: boolean;
    @Input() busy: boolean;

    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.unreferencedFeedback.indexOf(assessmentToDelete);
        this.unreferencedFeedback.splice(indexToDelete, 1);
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
            if (feedback.credits == null || isNaN(feedback.credits)) {
                this.assessmentsAreValid = false;
                return;
            }
        }
        this.assessmentsAreValid = true;
    }
    updateAssessment(feedback: Feedback) {
        this.validateFeedback();
    }
    public addReferencedFeedback(): void {
        const feedback = new Feedback();
        feedback.credits = 0;
        this.unreferencedFeedback.push(feedback);
        this.validateFeedback();
    }
}
