import { Injectable } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { parseJson } from 'app/foundation/util/json.util';

@Injectable({ providedIn: 'root' })
export class StructuredGradingCriterionService {
    /**
     * Connects the structured grading instructions with the feedback of a submission element
     * @param {Event} event - The drop event
     * @param {Feedback} feedback - The feedback of the assessment to be updated
     * the SGI element sent on drag in processed in this method
     * the corresponding drag method is in StructuredGradingInstructionsAssessmentLayoutComponent
     */
    updateFeedbackWithStructuredGradingInstructionEvent(feedback: Feedback, event: Event) {
        event.preventDefault();
        try {
            const data = (event as DragEvent).dataTransfer!.getData('text/plain');
            const instruction = parseJson<GradingInstruction>(data);
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
        const encounteredInstructions = new Map<number, number>();
        for (const feedback of assessments) {
            if (feedback.gradingInstruction) {
                score = this.calculateScoreForGradingInstructions(feedback, score, encounteredInstructions);
            } else {
                score += feedback.credits ?? 0;
            }
        }
        return score;
    }

    calculateScoreForGradingInstructions(feedback: Feedback, score: number, encounteredInstructions: Map<number, number>): number {
        const instructionId = feedback.gradingInstruction!.id!;
        const maxCount = feedback.gradingInstruction!.usageCount ?? 0;
        const encounters = encounteredInstructions.get(instructionId) ?? 0;

        encounteredInstructions.set(instructionId, encounters + 1);

        if (maxCount > 0 && encounters >= maxCount) {
            // Limited usage and limit already reached: do NOT add credits
            return score;
        }

        // Either unlimited (maxCount === 0) or limit not yet reached: add credits
        score += feedback.credits ?? 0;
        return score;
    }
}
