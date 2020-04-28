import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Injectable({ providedIn: 'root' })
export class StructuredGradingCriterionService {
    /**
     * Connects the structured grading instructions with the feedback of a submission element
     * @param {Event} event - The drop event
     * @param {Feedback} feedback - The feedback of the assessment to be updated
     * the SGI element sent on drag in processed in this method
     * the corresponding drag method is in StructuredGradingInstructionsAssessmentLayoutComponent
     */
    updateFeedbackWithStructuredGradingInstructionEvent(feedback: Feedback, event: any) {
        event.preventDefault();
        const data = event.dataTransfer.getData('text');
        const instruction = JSON.parse(data);
        feedback.credits = instruction.credits;
        feedback.detailText = instruction.feedback;
    }
}
