/**
 * Shared DTO interfaces used by exercise update DTOs across different exercise types.
 * These match the corresponding Java DTO structures on the server side.
 */

/**
 * DTO for competency reference (just the ID).
 */
export interface CompetencyDTO {
    id: number;
}

/**
 * DTO for competency links with weight.
 */
export interface CompetencyLinkDTO {
    competency: CompetencyDTO;
    weight: number;
}

/**
 * DTO for grading criterion.
 */
export interface GradingCriterionDTO {
    id?: number;
    title?: string;
    structuredGradingInstructions?: GradingInstructionDTO[];
}

/**
 * DTO for grading instruction.
 */
export interface GradingInstructionDTO {
    id?: number;
    credits?: number;
    gradingScale?: string;
    instructionDescription?: string;
    feedback?: string;
    usageCount?: number;
}
