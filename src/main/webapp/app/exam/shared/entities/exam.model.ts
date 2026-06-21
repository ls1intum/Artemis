import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { BaseEntity } from 'app/foundation/model/base-entity';

export enum ExamType {
    REAL = 'REAL',
    TEST = 'TEST',
    TEST_WITH_SIMULATION = 'TEST_WITH_SIMULATION',
}

export function testExamSimulationEndDate(exam?: Exam): dayjs.Dayjs | undefined {
    if (!(exam?.examType === ExamType.TEST_WITH_SIMULATION) || !exam?.startDate || exam.workingTime === undefined) {
        return undefined;
    }
    return exam.startDate.add(exam.workingTime, 'seconds');
}

export class Exam implements BaseEntity {
    public id?: number;
    public title?: string;
    public examType?: ExamType;
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
        this.examType = ExamType.REAL; // default value
        this.examWithAttendanceCheck = false; // default value

        // helper attributes (calculated by the server at the time of the last request)
        this.visible = false;
        this.started = false;
    }

    get testExam() {
        return this.examType !== ExamType.REAL;
    }

    set testExam(testExam: boolean) {
        if (testExam && this.examType == ExamType.REAL) {
            this.examType = ExamType.TEST;
        } else if (!testExam) {
            this.examType = ExamType.REAL;
        }
    }
}
