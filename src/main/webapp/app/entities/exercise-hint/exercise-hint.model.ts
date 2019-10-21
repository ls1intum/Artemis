import { Exercise } from 'app/entities/exercise';
import { BaseEntity } from 'app/shared';

export class ExerciseHint implements BaseEntity {
    id: number;
    public title: string;
    public content: string;
    public exercise: Exercise;
}
