import { BaseEntity } from 'app/shared';

export class StructuredGradingInstructionModel implements BaseEntity {
    id: number;
    public credit?: number;
    public gradingScale: string;
    public instructionDescription: string;
    public feedback: string;
    public usageCount?: number;
    public criterion: Criterion;
}
