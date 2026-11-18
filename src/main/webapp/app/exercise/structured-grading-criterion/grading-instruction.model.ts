import { BaseEntity } from 'app/foundation/model/base-entity';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class GradingInstruction implements BaseEntity {
    public id?: number;
    public credits!: number;
    public gradingScale!: string;
    public instructionDescription!: string;
    public feedback!: string;
    public usageCount?: number;
}
