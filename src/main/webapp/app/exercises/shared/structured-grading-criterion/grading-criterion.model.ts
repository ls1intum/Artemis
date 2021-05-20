import { BaseEntity } from 'app/shared/model/base-entity';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

export class GradingCriterion implements BaseEntity {
    public id?: number;
    public title: string;
    public structuredGradingInstructions: GradingInstruction[];
}
