import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';

export class StructuredGradingInstructionModel implements BaseEntity {
    id: number;
    public credit?: number;
    public gradingScale: string;
    public instructionDescription: string;
    public feedback: string;
    public usageCount?: number;
    public exercises: Exercise;
}
