import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';

export class AssessmentInstructions implements BaseEntity {
    id: number;
    public gradingInstruction: string;
    public feedback: string;
    public credit?: number;
    public usageCount?: number;
    public exercises: Exercise;
}
