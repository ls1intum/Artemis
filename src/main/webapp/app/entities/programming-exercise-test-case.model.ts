import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ProgrammingExerciseTestCase implements BaseEntity {
    id: number;
    testName: string;
    weight: number;
    bonusMultiplier: number;
    bonusPoints: number;
    active: boolean;
    afterDueDate: boolean;
    exercise: ProgrammingExercise;
}
