import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/services/programming-exercise.service';

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
    public programmingExerciseTask?: ProgrammingExerciseTask;
    public currentUserRating?: number;
}
