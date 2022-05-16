import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Task } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';

export class ExerciseHint implements BaseEntity {
    public id?: number;
    public title?: string;
    public content?: string;
    public exercise?: Exercise;
    public type?: string;
    public task?: Task;
}
