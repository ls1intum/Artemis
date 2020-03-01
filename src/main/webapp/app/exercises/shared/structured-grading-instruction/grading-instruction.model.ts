import { BaseEntity } from 'app/shared/model/base-entity';

export class GradingInstruction implements BaseEntity {
    id: number;
    public credits: number;
    public gradingScale: string;
    public instructionDescription: string;
    public feedback: string;
    public usageCount?: number;
}
