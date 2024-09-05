import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

export class BuildPlan implements BaseEntity {
    public id?: number;

    public buildPlan?: string;
    public programmingExercises?: Set<ProgrammingExercise>;
}
