import { Moment } from 'moment';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class Exam implements BaseEntity {
    public id: number;
    public course: Course;
    public studentExams: StudentExam[] | null;
    public exerciseGroups: ExerciseGroup[] | null;
    public dueDate: Moment | null;
    public releaseDate: Moment | null;
    public startText: string;
    public endText: string;
    public confirmationStartText: string;
    public confirmationEndText: string;
    public maxScore: number | null;
}
