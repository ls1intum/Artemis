import { Exercise } from 'app/entities/exercise/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ExerciseHint implements BaseEntity {
    id: number;
    public title: string;
    public content: string;
    public exercise: Exercise;
}
