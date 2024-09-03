import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

export const enum ErrorType {
    TEMPLATE_REPO_MISSING = 'TEMPLATE_REPO_MISSING',
    SOLUTION_REPO_MISSING = 'SOLUTION_REPO_MISSING',
    AUXILIARY_REPO_MISSING = 'AUXILIARY_REPO_MISSING',
    TEST_REPO_MISSING = 'TEST_REPO_MISSING',
    TEMPLATE_BUILD_PLAN_MISSING = 'TEMPLATE_BUILD_PLAN_MISSING',
    SOLUTION_BUILD_PLAN_MISSING = 'SOLUTION_BUILD_PLAN_MISSING',
}

export class ConsistencyCheckError implements BaseEntity {
    public id?: number;
    public type?: ErrorType;
    public programmingExercise?: ProgrammingExercise;
    public programmingExerciseCourseId?: number;
}
