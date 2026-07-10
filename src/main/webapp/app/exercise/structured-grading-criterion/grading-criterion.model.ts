import { BaseEntity } from 'app/foundation/model/base-entity';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class GradingCriterion implements BaseEntity {
    public id?: number;
    public title!: string;
    public structuredGradingInstructions!: GradingInstruction[];
}
