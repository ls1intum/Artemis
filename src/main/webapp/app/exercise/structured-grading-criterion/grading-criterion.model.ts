import { BaseEntity } from 'app/foundation/model/base-entity';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

export class GradingCriterion implements BaseEntity {
    public id?: number;
    public title: string;
    public structuredGradingInstructions: GradingInstruction[];
}
