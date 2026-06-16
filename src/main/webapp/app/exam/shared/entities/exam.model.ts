import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { BaseEntity } from 'app/foundation/model/base-entity';

export function isSimulationAndPracticeExam(exam?: Exam): boolean {
    return exam?.testExam === true && exam?.hasSimulation === true;
}

export function testExamSimulationEndDate(exam?: Exam): dayjs.Dayjs | undefined {
    if (!isSimulationAndPracticeExam(exam) || !exam?.startDate || exam.workingTime === undefined) {
        return undefined;
    }
    return exam.startDate.add(exam.workingTime, 'seconds');
}

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
    // if the test exam has simulation attempt at the start
    public hasSimulation?: boolean;
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
