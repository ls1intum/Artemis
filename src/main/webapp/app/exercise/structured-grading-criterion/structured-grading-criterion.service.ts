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
        try {
            const data = event.dataTransfer.getData('text/plain');
            const instruction = JSON.parse(data);
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        } catch (err) {
            // Rethrow any non syntax error. syntax errors are caused by invalid JSON if someone drops something unrelated, ignore them
            if (!(err instanceof SyntaxError)) {
                throw err;
            }
        }
    }
    computeTotalScore(assessments: Feedback[]) {
        let score = 0;
        const gradingInstructions = {}; // { instructionId: noOfEncounters }
        for (const feedback of assessments) {
            if (feedback.gradingInstruction) {
                score = this.calculateScoreForGradingInstructions(feedback, score, gradingInstructions);
            } else {
                score += feedback.credits!;
            }
        }
        return score;
    }

    calculateScoreForGradingInstructions(feedback: Feedback, score: number, gradingInstructions: any): number {
        if (gradingInstructions[feedback.gradingInstruction!.id!]) {
            // We Encountered this grading instruction before
            const maxCount = feedback.gradingInstruction!.usageCount;
            const encounters = gradingInstructions[feedback.gradingInstruction!.id!];
            if (maxCount && maxCount > 0) {
                if (encounters >= maxCount) {
                    gradingInstructions[feedback.gradingInstruction!.id!] = encounters + 1;
                } else {
                    gradingInstructions[feedback.gradingInstruction!.id!] = encounters + 1;
                    score += feedback.gradingInstruction!.credits;
                }
            } else {
                score += feedback.credits!;
            }
        } else {
            // First time encountering the grading instruction
            gradingInstructions[feedback.gradingInstruction!.id!] = 1;
            score += feedback.credits!;
        }
        return score;
    }
}
