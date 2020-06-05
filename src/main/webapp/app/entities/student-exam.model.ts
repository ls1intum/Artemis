import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';

export class StudentExam {
    public id: number;
    public student: User;
    public exam: Exam;
    public exercises: Exercise[];
}
