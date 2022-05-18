import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Task } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';

export enum HintType {
    TEXT = 'text',
    CODE = 'code',
}

export class ExerciseHint implements BaseEntity {
    public id?: number;
    public title?: string;
    public content?: string;
    public exercise?: Exercise;
    public type?: HintType;
    public task?: Task;
}
