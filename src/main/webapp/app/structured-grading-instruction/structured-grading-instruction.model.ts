import { BaseEntity } from 'app/shared';
import { StructuredGradingCriterion } from 'app/structured-grading-criterion/structured-grading-criterion.model';

export class StructuredGradingInstructionModel implements BaseEntity {
    id: number;
    public credit?: number;
    public gradingScale: string;
    public instructionDescription: string;
    public feedback: string;
    public usageCount?: number;
    public criterion: StructuredGradingCriterion;
}
