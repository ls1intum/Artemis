import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
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

    constructor(private structuredGradingCriterionService: StructuredGradingCriterionService) {}

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

    createAssessmentOnDrop(event: Event) {
        this.addUnreferencedFeedback();
        const newFeedback: Feedback | undefined = this.unreferencedFeedback.last();
        if (newFeedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(newFeedback, event);
            this.updateAssessment(newFeedback);
        }
    }
}
