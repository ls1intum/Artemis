import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import {
    APP_NAME_PATTERN_FOR_SWIFT,
    PACKAGE_NAME_PATTERN_FOR_DART,
    PACKAGE_NAME_PATTERN_FOR_GO,
    PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX,
    PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN,
} from 'app/foundation/constants/input.constants';
import { ProblemStatementGlobalRefinementRequest } from 'app/openapi/model/problem-statement-global-refinement-request';
import { ProblemStatementTargetedRefinementRequest } from 'app/openapi/model/problem-statement-targeted-refinement-request';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problem-statement-generation-request';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problem-statement-refinement-response';
import { ProblemStatementGenerationResponse } from 'app/openapi/model/problem-statement-generation-response';

/** Maximum allowed length for user prompts. Must match HyperionUtils.MAX_USER_PROMPT_LENGTH on the server. */
export const MAX_USER_PROMPT_LENGTH = 1000;

/** Maximum allowed length for problem statements. Must match HyperionUtils.MAX_PROBLEM_STATEMENT_LENGTH on the server. */
export const MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

/** Fraction of MAX_USER_PROMPT_LENGTH at which the character counter shows a warning. */
export const PROMPT_LENGTH_WARNING_THRESHOLD = 0.9;

/**
 * Approximate pixel width of the expanded inline refinement prompt.
 * Derived from the `min-width: 20rem` rule in
 * `app/editor/monaco-editor/inline-refinement-button/inline-refinement-button.component.scss`
 * (≈ 320 px at default font size) plus padding and action buttons.
 * Keep in sync with that SCSS value.
 */
export const INLINE_REFINEMENT_PROMPT_WIDTH_PX = 370;

/** Maximum allowed length for refinement instructions. Must match HyperionUtils.MAX_INSTRUCTION_LENGTH on the server. */
export const MAX_INSTRUCTION_LENGTH = 500;

/** Matches `\r\n` (Windows) and standalone `\r` (old Mac) line endings in a single pass. */
const CARRIAGE_RETURN_PATTERN = /\r\n?/g;

/**
 * Position information for a text selection in the editor.
 * Column values follow Monaco conventions: 1-indexed, with endColumn being exclusive.
 */
export interface InstructionSelectionPosition {
    startLine: number;
    endLine: number;
    startColumn: number;
    /** Exclusive end column (1-indexed) — points after the last selected character. */
    endColumn: number;
}

/**
 * Event structure for inline refinement requests from the editor.
 * Column values follow Monaco conventions: 1-indexed, with endColumn being exclusive
 * (pointing to the character after the last selected character).
 */
export interface InlineRefinementEvent extends InstructionSelectionPosition {
    instruction: string;
}

/**
 * Full selection event emitted by the editor, combining position data with
 * the selected text and its screen coordinates for floating UI placement.
 */
export interface EditorSelectionWithPosition extends InstructionSelectionPosition {
    selectedText: string;
    screenPosition: { top: number; left: number };
}

/**
 * Normalizes a string by trimming whitespace and normalizing line endings.
 * This helps compare problem statements that might have formatting differences.
 */
export function normalizeString(str: string | undefined): string {
    if (!str) return '';
    return str.replace(CARRIAGE_RETURN_PATTERN, '\n').trim();
}

/**
 * Extracts the course ID from a programming exercise, handling both direct course
 * and exam-based exercises.
 */
export function getCourseId(exercise: ProgrammingExercise | undefined): number | undefined {
    return exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
}

/**
 * Determines whether the problem statement should trigger a "Generate" action
 * (as opposed to "Refine"). Returns true if the problem statement is empty,
 * or matches the template exactly.
 */
export function isTemplateOrEmpty(problemStatement: string | undefined, template: string, templateLoaded: boolean): boolean {
    if (!problemStatement || problemStatement.trim() === '') {
        return true;
    }

    if (!templateLoaded) {
        return false;
    }

    const normalizedProblemStatement = normalizeString(problemStatement);
    const normalizedTemplate = normalizeString(template);

    return normalizedTemplate !== '' && normalizedProblemStatement === normalizedTemplate;
}

/**
 * Validates that a refinement response contains a non-empty refined problem statement.
 */
export function isValidRefinementResponse(response: ProblemStatementRefinementResponse | undefined): boolean {
    return !!(response?.refinedProblemStatement && response.refinedProblemStatement.trim() !== '');
}

/**
 * Validates that a generation response contains a non-empty draft problem statement.
 */
export function isValidGenerationResponse(response: ProblemStatementGenerationResponse | undefined): boolean {
    return !!(response?.draftProblemStatement && response.draftProblemStatement.trim() !== '');
}

/**
 * Builds a global refinement request payload.
 */
export function buildGlobalRefinementRequest(problemStatementText: string, userPrompt: string): ProblemStatementGlobalRefinementRequest {
    return {
        problemStatementText: problemStatementText,
        userPrompt: userPrompt.trim(),
    };
}

/**
 * Builds a targeted refinement request payload from an inline refinement event.
 */
export function buildTargetedRefinementRequest(problemStatementText: string, event: InlineRefinementEvent): ProblemStatementTargetedRefinementRequest {
    return {
        problemStatementText: problemStatementText,
        startLine: event.startLine,
        endLine: event.endLine,
        startColumn: event.startColumn,
        endColumn: event.endColumn,
        instruction: event.instruction.trim(),
    };
}

/**
 * Builds a problem statement generation request payload.
 */
export function buildGenerationRequest(userPrompt: string): ProblemStatementGenerationRequest {
    return {
        userPrompt: userPrompt.trim(),
    };
}

const MAX_PROPOSED_PACKAGE_NAME_LENGTH = 32;

/** Extracts a title from the first level-one Markdown heading. */
export function extractProblemStatementTitle(problemStatement: string | undefined): string | undefined {
    const match = problemStatement?.match(/^#\s+(.+)$/m);
    const title = match?.[1]?.replace(/[*_`~]/g, '').trim();
    return title ? title : undefined;
}

function sanitizeExerciseTitle(title: string): string | undefined {
    const sanitized = title
        .normalize('NFKD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z0-9 _-]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    return sanitized.length >= 3 ? sanitized : undefined;
}

/** Derives a valid package or app name from an exercise title. */
export function deriveProposedPackageName(title: string, language: ProgrammingLanguage, projectType?: ProjectType): string | undefined {
    const words = title
        .normalize('NFKD')
        .replace(/[\u0300-\u036f]/g, '')
        .split(/[^a-zA-Z0-9]+/)
        .filter(Boolean);
    const pattern = packageNamePatternFor(language, projectType);
    if (!pattern || words.length === 0) {
        return undefined;
    }
    const pascalCase = language === ProgrammingLanguage.SWIFT;
    const joined = words.map((word) => (pascalCase ? word.charAt(0).toUpperCase() + word.slice(1).toLowerCase() : word.toLowerCase())).join('');
    const candidate = joined.replace(/^[0-9]+/, '').substring(0, MAX_PROPOSED_PACKAGE_NAME_LENGTH);
    if (!candidate) {
        return undefined;
    }
    for (const proposal of [candidate, pascalCase ? `${candidate}Exercise` : `${candidate}exercise`]) {
        if (new RegExp(pattern).test(proposal)) {
            return proposal;
        }
    }
    return undefined;
}

function packageNamePatternFor(language: ProgrammingLanguage, projectType?: ProjectType): string | undefined {
    switch (language) {
        case ProgrammingLanguage.JAVA:
        case ProgrammingLanguage.KOTLIN:
            return projectType === ProjectType.MAVEN_BLACKBOX ? PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX : PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN;
        case ProgrammingLanguage.SWIFT:
            return APP_NAME_PATTERN_FOR_SWIFT;
        case ProgrammingLanguage.GO:
            return PACKAGE_NAME_PATTERN_FOR_GO;
        case ProgrammingLanguage.DART:
            return PACKAGE_NAME_PATTERN_FOR_DART;
        default:
            return undefined;
    }
}

export interface DraftMetadataPrefill {
    title?: string;
    packageName?: string;
}

/** Proposes blank creation-form metadata from an AI-generated draft without mutating the exercise. */
export function deriveDraftMetadataPrefill(exercise: ProgrammingExercise, draftProblemStatement: string): DraftMetadataPrefill | undefined {
    if (exercise.id) {
        return undefined;
    }
    const heading = extractProblemStatementTitle(draftProblemStatement);
    if (!heading) {
        return undefined;
    }
    const prefill: DraftMetadataPrefill = {};
    if (!exercise.title?.trim()) {
        const title = sanitizeExerciseTitle(heading);
        if (title) {
            prefill.title = title;
        }
    }
    if (!exercise.packageName?.trim() && exercise.programmingLanguage) {
        const packageName = deriveProposedPackageName(heading, exercise.programmingLanguage, exercise.projectType);
        if (packageName) {
            prefill.packageName = packageName;
        }
    }
    return Object.keys(prefill).length > 0 ? prefill : undefined;
}
