import { GradingInstruction } from 'app/exercises/shared/structured-grading-instruction/grading-instruction.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class GradingCriterion implements BaseEntity {
    id: number;
    public title: string;
    public structuredGradingInstructions: GradingInstruction[];
}
