import { ExamUser } from 'app/entities/exam/exam-user.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class Exam implements BaseEntity {
    public id?: number;
    public title?: string;
    public testExam?: boolean;
    public examWithAttendanceCheck?: boolean;
    public visibleDate?: dayjs.Dayjs;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    // Default exam working time in seconds
    public workingTime?: number;
    public publishResultsDate?: dayjs.Dayjs;
    public examStudentReviewStart?: dayjs.Dayjs;
    public examStudentReviewEnd?: dayjs.Dayjs;
    public exampleSolutionPublicationDate?: dayjs.Dayjs;
    // grace period in seconds - time in which students can still submit even though working time is over
    public gracePeriod?: number;
    public examiner?: string;
    public moduleNumber?: string;
    public courseName?: string;

    public startText?: string;
    public endText?: string;
    public confirmationStartText?: string;
    public confirmationEndText?: string;
    public examMaxPoints?: number;
    public randomizeExerciseOrder?: boolean;
    public numberOfExercisesInExam?: number;
    public numberOfCorrectionRoundsInExam?: number;
    public course?: Course;
    public exerciseGroups?: ExerciseGroup[];
    public studentExams?: StudentExam[];
    public examUsers?: ExamUser[];
    public quizExamMaxPoints?: number;
    public numberOfExamUsers?: number; // transient
    public channelName?: string; // transient

    // helper attributes
    public visible?: boolean;
    public started?: boolean;

    public examArchivePath?: string;

    public latestIndividualEndDate?: dayjs.Dayjs;

    constructor() {
        this.randomizeExerciseOrder = false; // default value (set by server)
        this.numberOfCorrectionRoundsInExam = 1; // default value
        this.examMaxPoints = 1; // default value
        this.workingTime = 0; // will be updated during creation
        this.testExam = false; // default value
        this.examWithAttendanceCheck = false; // default value

        // helper attributes (calculated by the server at the time of the last request)
        this.visible = false;
        this.started = false;
    }
}
