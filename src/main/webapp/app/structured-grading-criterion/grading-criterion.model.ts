import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';
import { GradingInstruction } from 'app/structured-grading-instruction/grading-instruction.model';

export class GradingCriterion implements BaseEntity {
    id: number;
    public title: string;
    public gradingInstructions: GradingInstruction[];
}
