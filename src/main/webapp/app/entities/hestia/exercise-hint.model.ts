import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';

export enum HintType {
    TEXT = 'text',
    CODE = 'code',
}

export class ExerciseHint implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public content?: string;
    public exercise?: Exercise;
    public type?: HintType;
    public programmingExerciseTask?: ProgrammingExerciseServerSideTask;
    public currentUserRating?: number;
}
