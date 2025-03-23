import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';

export class ProgrammingExerciseServerSideTask implements BaseEntity {
    public id?: number;
    public taskName?: string;
    public testCases?: ProgrammingExerciseTestCase[];
}
