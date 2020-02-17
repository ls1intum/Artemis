import { GradingInstruction } from 'app/structured-grading-instruction/grading-instruction.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class GradingCriterion implements BaseEntity {
    id: number;
    public title: string;
    public gradingInstructions: GradingInstruction[];
}
