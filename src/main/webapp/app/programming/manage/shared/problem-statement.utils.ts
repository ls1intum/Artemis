import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProblemStatementGlobalRefinementRequest } from 'app/openapi/model/problemStatementGlobalRefinementRequest';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';
import { ProblemStatementGenerationResponse } from 'app/openapi/model/problemStatementGenerationResponse';

/**
 * Normalizes a string by trimming whitespace and normalizing line endings.
 * This helps compare problem statements that might have formatting differences.
 */
export function normalizeString(str: string | undefined): string {
    if (!str) return '';
    return str.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
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
        return true;
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
        problemStatementText: problemStatementText ?? '',
        userPrompt: (userPrompt ?? '').trim(),
    };
}

/**
 * Builds a problem statement generation request payload.
 */
export function buildGenerationRequest(userPrompt: string): ProblemStatementGenerationRequest {
    return {
        userPrompt: (userPrompt ?? '').trim(),
    };
}
