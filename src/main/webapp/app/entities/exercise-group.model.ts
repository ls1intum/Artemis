import { Exercise } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';

export class ExerciseGroup {
    public id: number;
    public exercises: Exercise[];
    public exam: Exam;
    public mandatory: boolean;
}
