import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ExerciseHint implements BaseEntity {
    public id?: number;
    public type?: string;
    public title?: string;
    public content?: string;
    public exercise?: Exercise;
}
