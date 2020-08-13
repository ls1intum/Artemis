import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class StaticCodeAnalysisCategory implements BaseEntity {
    id: number;
    tool: string;
    name: string;
    description: string;
    perIssuePenalty: number;
    maxPenalty: number;
    active: boolean;
    exercise: ProgrammingExercise;
}
