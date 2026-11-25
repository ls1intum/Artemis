import { Service, inject } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { parseJson } from 'app/foundation/util/json.util';
import { getPositiveAndCappedTotalScore } from 'app/exercise/util/exercise.utils';

/** Score of a complete assessment, broken down the way it is presented to and saved for a tutor. */
export interface AssessmentScore {
    /** Points a single feedback adds to the score. Zero once the usage limit of its grading instruction is reached. */
    contributions: Map<Feedback, number>;
    /** Sum of all positive contributions, including the (capped) automatic test points. */
    awarded: number;
    /** Sum of all negative contributions. Zero or negative. */
    deducted: number;
    /** {@code awarded + deducted}, floored at zero and capped at {@code maxPoints}. */
    total: number;
}

@Service()
export class StructuredGradingCriterionService {
    private readonly selectionService = inject(GradingInstructionSelectionService);

    /**
     * Links the grading instruction dragged from StructuredGradingInstructionsAssessmentLayoutComponent to the feedback.
     * @param feedback the feedback the instruction was dropped onto
     * @param event the drop event
     * @returns whether the instruction was linked; false for foreign drag data or an exhausted usage limit
     */
    updateFeedbackWithStructuredGradingInstructionEvent(feedback: Feedback, event: Event): boolean {
        event.preventDefault();
        const data = (event as DragEvent).dataTransfer?.getData('text/plain');
        if (!data) {
            return false;
        }
        let instruction: GradingInstruction | null;
        try {
            instruction = parseJson<GradingInstruction | null>(data);
        } catch (err) {
            // Something unrelated was dropped, so the data is no instruction JSON.
            if (err instanceof SyntaxError) {
                return false;
            }
            throw err;
        }
        // Valid JSON from another drag source, such as selected text, is no serialized instruction.
        if (typeof instruction !== 'object' || instruction === null || instruction.id === undefined) {
            return false;
        }
        return this.applyInstructionToFeedback(feedback, instruction);
    }

    /**
     * Links the armed instruction to the feedback and disarms it.
     * @returns whether an instruction was linked
     */
    applyArmedInstructionToFeedback(feedback: Feedback): boolean {
        const instruction = this.selectionService.consumeArmedInstruction();
        return !!instruction && this.applyInstructionToFeedback(feedback, instruction);
    }

    /** Every way of linking an instruction ends here, so the usage limit applies to all of them. Relinking the same instruction adds no application. */
    private applyInstructionToFeedback(feedback: Feedback, instruction: GradingInstruction): boolean {
        if (feedback.gradingInstruction?.id !== instruction.id && !this.selectionService.canApplyInstruction(instruction)) {
            return false;
        }
        feedback.gradingInstruction = instruction;
        feedback.credits = instruction.credits;
        return true;
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

    /**
     * Scores a complete assessment the way saving it does, so that a displayed score cannot disagree with the
     * stored one: a grading instruction stops adding credits once its usage limit is reached, the automatic test
     * points are capped before the manually awarded points are added, and the result is floored at zero and capped
     * at the maximum points of the exercise.
     *
     * @param feedbacks all feedback of the assessment, in the order in which it is saved
     * @param maxPoints maximum points of the exercise including bonus points
     * @param capAutomaticTestSubtotal whether the automatic test points are summed separately and capped before the
     *                                 manual points are added. Only programming exercises grade that way.
     */
    computeAssessmentScore(feedbacks: Feedback[], maxPoints: number, capAutomaticTestSubtotal = false): AssessmentScore {
        const contributions = new Map<Feedback, number>();
        const encounteredInstructions = new Map<number, number>();
        let manualScore = 0;
        let automaticTestScore = 0;
        let awarded = 0;
        let deducted = 0;

        for (const feedback of feedbacks) {
            if (capAutomaticTestSubtotal && feedback.type === FeedbackType.AUTOMATIC && !Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                const credits = feedback.credits ?? 0;
                automaticTestScore += credits;
                contributions.set(feedback, credits);
                continue;
            }

            const scoreBeforeFeedback = manualScore;
            if (feedback.gradingInstruction) {
                manualScore = this.calculateScoreForGradingInstructions(feedback, manualScore, encounteredInstructions);
            } else {
                manualScore += feedback.credits ?? 0;
            }

            const contribution = manualScore - scoreBeforeFeedback;
            contributions.set(feedback, contribution);
            if (contribution > 0) {
                awarded += contribution;
            } else if (contribution < 0) {
                deducted += contribution;
            }
        }

        const cappedAutomaticTestScore = Math.min(automaticTestScore, maxPoints);
        if (cappedAutomaticTestScore > 0) {
            awarded += cappedAutomaticTestScore;
        } else if (cappedAutomaticTestScore < 0) {
            deducted += cappedAutomaticTestScore;
        }

        return { contributions, awarded, deducted, total: getPositiveAndCappedTotalScore(awarded + deducted, maxPoints) };
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
