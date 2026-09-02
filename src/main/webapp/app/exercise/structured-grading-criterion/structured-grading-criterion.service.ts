import { Injectable, inject } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
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

@Injectable({ providedIn: 'root' })
export class StructuredGradingCriterionService {
    private readonly selectionService = inject(GradingInstructionSelectionService);

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
            const data = (event as DragEvent).dataTransfer?.getData('text/plain');
            if (!data) {
                return;
            }
            this.applyInstructionToFeedback(feedback, parseJson<GradingInstruction>(data));
        } catch (err) {
            // Rethrow any non syntax error. syntax errors are caused by invalid JSON if someone drops something unrelated, ignore them
            if (!(err instanceof SyntaxError)) {
                throw err;
            }
        }
    }

    /**
     * Keyboard stand-in for drop: applies and clears a previously armed instruction onto {@code feedback}.
     * @returns whether an instruction was applied
     */
    applyArmedInstructionToFeedback(feedback: Feedback): boolean {
        const instruction = this.selectionService.consumeArmedInstruction();
        if (!instruction) {
            return false;
        }
        this.applyInstructionToFeedback(feedback, instruction);
        return true;
    }

    private applyInstructionToFeedback(feedback: Feedback, instruction: GradingInstruction | undefined): void {
        if (!instruction) {
            return;
        }
        feedback.gradingInstruction = instruction;
        feedback.credits = instruction.credits;
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

    findCriterionTitle(gradingCriteria: GradingCriterion[] | undefined, instructionId: number | undefined): string | undefined {
        if (!gradingCriteria || instructionId === undefined) {
            return undefined;
        }
        for (const criterion of gradingCriteria) {
            if (criterion.structuredGradingInstructions?.some((instruction) => instruction.id === instructionId)) {
                return criterion.title || undefined;
            }
        }
        return undefined;
    }
}
