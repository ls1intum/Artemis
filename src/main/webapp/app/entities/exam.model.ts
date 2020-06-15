import { Moment } from 'moment';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class Exam implements BaseEntity {
    public id: number;
    public startDate: Moment | null;
    public endDate: Moment | null;
    public visibleDate: Moment | null;
    public startText: string;
    public endText: string;
    public confirmationStartText: string;
    public confirmationEndText: string;
    public maxPoints: number | null;
    public numberOfExercisesInExam: number | null;
    public randomizeExerciseOrder = false; // default value (set by server)
    public course: Course;
    public studentExams: StudentExam[] | null;
    public exerciseGroups: ExerciseGroup[] | null;
}
