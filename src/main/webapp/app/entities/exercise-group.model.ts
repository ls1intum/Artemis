import { Exercise } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ExerciseGroup implements BaseEntity {
    public id: number;
    public exercises: Exercise[];
    public exam: Exam;
    public mandatory: boolean;
}
