import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ExamSession } from 'app/entities/exam/exam-session.model';

export class StudentExam implements BaseEntity {
    public id?: number;
    /**
     * The individual working time per student in seconds
     * The default working time of an exam is stored in exam.workingTime
     */
    public workingTime?: number;
    public submitted?: boolean;
    public started?: boolean;
    public testRun?: boolean;
    public submissionDate?: dayjs.Dayjs;
    public user?: User;
    public exam?: Exam;
    public exercises?: Exercise[];
    public examSessions?: ExamSession[];
    public startedDate?: dayjs.Dayjs;

    // helper attribute
    public ended?: boolean;
    public numberOfExamSessions = 0; // default value

    constructor() {
        // helper attribute (calculated by the server at the time of the last request)
        this.ended = false;
    }
}
