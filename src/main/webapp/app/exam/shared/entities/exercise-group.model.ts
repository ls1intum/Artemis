import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ExerciseGroup implements BaseEntity {
    public id?: number;
    public title?: string;
    public isMandatory?: boolean;
    public exam?: Exam;
    public exercises?: Exercise[];
}
