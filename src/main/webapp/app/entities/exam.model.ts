import { Moment } from 'moment';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export class Exam implements BaseEntity {
    public id: number;
    public title: String;
    public visibleDate: Moment | null;
    public startDate: Moment | null;
    public endDate: Moment | null;
    public startText: string;
    public endText: string;
    public confirmationStartText: string;
    public confirmationEndText: string;
    public maxPoints: number | null;
    public randomizeExerciseOrder = false; // default value (set by server)
    public numberOfExercisesInExam: number | null;
    public course: Course;
    public exerciseGroups: ExerciseGroup[] | null;
    public studentExams: StudentExam[] | null;
    public registeredUsers: User[] | null;
    public numberOfRegisteredUsers?: number; // transient
}
