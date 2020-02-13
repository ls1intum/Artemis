import { BaseEntity } from 'app/shared';
import { GradingCriterion } from 'app/structured-grading-criterion/grading-criterion.model';

export class GradingInstruction implements BaseEntity {
    id: number;
    public credit?: number;
    public gradingScale: string;
    public instructionDescription: string;
    public feedback: string;
    public usageCount?: number;
}
