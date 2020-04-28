import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Injectable({ providedIn: 'root' })
export class StructuredGradingCriterionService {
    updateFeedbackWithStructuredGradingInstructionEvent(feedback: Feedback, event: any) {
        event.preventDefault();
        const data = event.dataTransfer.getData('text');
        const instruction = JSON.parse(data);
        feedback.credits = instruction.credits;
        feedback.detailText = instruction.feedback;
    }
}
