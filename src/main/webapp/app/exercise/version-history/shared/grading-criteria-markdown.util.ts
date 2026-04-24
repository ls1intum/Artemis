import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingCriterionSnapshotDTO, GradingInstructionSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { GradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { GradingCriterionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-criterion.action';
import { GradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-feedback.action';
import { GradingInstructionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-instruction.action';
import { GradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { GradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';

type SnapshotOrModelCriterion = GradingCriterionSnapshotDTO | GradingCriterion;
type SnapshotOrModelInstruction = GradingInstructionSnapshotDTO | GradingInstruction;

/** Creates a canonical markdown document from grading instructions and criteria for read-only diff rendering. */
export function serializeGradingCriteriaToMarkdown(gradingInstructions?: string, gradingCriteria?: SnapshotOrModelCriterion[]): string | undefined {
    const topLevelInstructions = gradingInstructions?.trim();
    const criteria = (gradingCriteria ?? []).map((criterion) => normalizeCriterion(criterion)).filter((criterion) => criterion.instructions.length > 0 || criterion.title);

    if (!topLevelInstructions && criteria.length === 0) {
        return undefined;
    }

    let markdown = `${topLevelInstructions ?? ''}\n\n`;
    for (const criterion of criteria) {
        if (criterion.title) {
            markdown += `${GradingCriterionAction.IDENTIFIER} ${criterion.title}\n\t`;
        }
        markdown += criterion.instructions.map((instruction) => serializeInstruction(instruction)).join('');
    }

    return markdown.trimEnd();
}

interface NormalizedCriterion {
    title?: string;
    instructions: NormalizedInstruction[];
}

interface NormalizedInstruction {
    credits?: number;
    gradingScale?: string;
    instructionDescription?: string;
    feedback?: string;
    usageCount?: number;
}

function normalizeCriterion(criterion: SnapshotOrModelCriterion): NormalizedCriterion {
    const instructions = (criterion.structuredGradingInstructions ?? []).map((instruction) => normalizeInstruction(instruction));
    return {
        title: criterion.title?.trim() || undefined,
        instructions,
    };
}

function normalizeInstruction(instruction: SnapshotOrModelInstruction): NormalizedInstruction {
    return {
        credits: instruction.credits,
        gradingScale: instruction.gradingScale,
        instructionDescription: instruction.instructionDescription,
        feedback: instruction.feedback,
        usageCount: instruction.usageCount,
    };
}

function serializeInstruction(instruction: NormalizedInstruction): string {
    const credits = instruction.credits ?? Number(GradingCreditsAction.TEXT);
    const gradingScale = instruction.gradingScale ?? GradingScaleAction.TEXT;
    const description = instruction.instructionDescription ?? GradingDescriptionAction.TEXT;
    const feedback = instruction.feedback ?? GradingFeedbackAction.TEXT;
    const usageCount = instruction.usageCount ?? Number(GradingUsageCountAction.TEXT);

    return (
        `${GradingInstructionAction.IDENTIFIER}\n` +
        `\t${GradingCreditsAction.IDENTIFIER} ${credits}\n` +
        `\t${GradingScaleAction.IDENTIFIER} ${gradingScale}\n` +
        `\t${GradingDescriptionAction.IDENTIFIER} ${description}\n` +
        `\t${GradingFeedbackAction.IDENTIFIER} ${feedback}\n` +
        `\t${GradingUsageCountAction.IDENTIFIER} ${usageCount}\n\n`
    );
}
