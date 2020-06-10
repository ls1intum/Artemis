import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class StudentExam implements BaseEntity {
    public id: number;
    public student: User;
    public exam: Exam;
    public exercises: Exercise[];
}
