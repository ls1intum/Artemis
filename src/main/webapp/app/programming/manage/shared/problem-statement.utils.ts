import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProblemStatementGlobalRefinementRequest } from 'app/openapi/model/problemStatementGlobalRefinementRequest';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';
import { ProblemStatementGenerationResponse } from 'app/openapi/model/problemStatementGenerationResponse';

/** Maximum allowed length for user prompts. Must match HyperionPromptSanitizer.MAX_USER_PROMPT_LENGTH on the server. */
export const MAX_USER_PROMPT_LENGTH = 1000;

/** Maximum allowed length for problem statements. Must match HyperionPromptSanitizer.MAX_PROBLEM_STATEMENT_LENGTH on the server. */
export const MAX_PROBLEM_STATEMENT_LENGTH = 50_000;

/** Fraction of MAX_USER_PROMPT_LENGTH at which the character counter shows a warning. */
export const PROMPT_LENGTH_WARNING_THRESHOLD = 0.9;

/** Matches `\r\n` (Windows) and standalone `\r` (old Mac) line endings in a single pass. */
const CARRIAGE_RETURN_PATTERN = /\r\n?/g;

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
 * Builds a problem statement generation request payload.
 */
export function buildGenerationRequest(userPrompt: string): ProblemStatementGenerationRequest {
    return {
        userPrompt: userPrompt.trim(),
    };
}
