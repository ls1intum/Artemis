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
    public publishResultsDate: Moment | null;
    public examStudentReviewStart: Moment | null;
    public examStudentReviewEnd: Moment | null;
    /**
     * grace period in seconds - time in which students can still submit even though working time is over
     */
    public gracePeriod: number;

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

    // helper attributes (calculated by the server at the time of the last request)
    public visible = false;
    public started = false;
}
