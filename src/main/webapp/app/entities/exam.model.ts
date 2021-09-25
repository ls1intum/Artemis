import dayjs from 'dayjs';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export class Exam implements BaseEntity {
    public id?: number;
    public title?: string;
    public visibleDate?: dayjs.Dayjs;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    public publishResultsDate?: dayjs.Dayjs;
    public examStudentReviewStart?: dayjs.Dayjs;
    public examStudentReviewEnd?: dayjs.Dayjs;
    /**
     * grace period in seconds - time in which students can still submit even though working time is over
     */
    public gracePeriod?: number;
    public examiner?: string;
    public moduleNumber?: string;
    public courseName?: string;

    public startText?: string;
    public endText?: string;
    public confirmationStartText?: string;
    public confirmationEndText?: string;
    public maxPoints?: number;
    public randomizeExerciseOrder?: boolean;
    public numberOfExercisesInExam?: number;
    public numberOfCorrectionRoundsInExam?: number;
    public course?: Course;
    public exerciseGroups?: ExerciseGroup[];
    public studentExams?: StudentExam[];
    public registeredUsers?: User[];

    public numberOfRegisteredUsers?: number; // transient

    // helper attributes
    public visible?: boolean;
    public started?: boolean;

    public examArchivePath?: string;

    public latestIndividualEndDate?: dayjs.Dayjs;

    constructor() {
        this.randomizeExerciseOrder = false; // default value (set by server)
        this.numberOfCorrectionRoundsInExam = 1; // default value
        this.maxPoints = 1; // default value

        // helper attributes (calculated by the server at the time of the last request)
        this.visible = false;
        this.started = false;
    }
}
