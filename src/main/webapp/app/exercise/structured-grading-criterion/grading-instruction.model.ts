import { BaseEntity } from 'app/foundation/model/base-entity';

export class GradingInstruction implements BaseEntity {
    public id?: number;
    public credits: number;
    public gradingScale: string;
    public instructionDescription: string;
    public feedback: string;
    public usageCount?: number;
}
