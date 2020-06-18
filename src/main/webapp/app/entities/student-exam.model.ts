import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Participation } from 'app/entities/participation/participation.model';

export class StudentExam implements BaseEntity {
    public id: number;
    public student: User;
    public exam: Exam;
    public exercises: Exercise[];
    public participations: Participation[];
}
